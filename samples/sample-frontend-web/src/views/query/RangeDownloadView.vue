<script setup lang="ts">
import {computed, ref} from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import {ossApi} from '@/api/oss'

// ── 常量配置 ──────────────────────────────────────────────────────────
const CHUNK_SIZE = 5 * 1024 * 1024            // 5 MB / 片（分片下载大小）
const CONCURRENCY = 3                          // 同时下载的分片数
const MAX_RETRY = 2                            // 单片失败最大重试次数
const SMALL_FILE_THRESHOLD = 10 * 1024 * 1024  // < 10 MB 建议普通下载

// ── 状态 ──────────────────────────────────────────────────────────────
const downloadKey = ref('demo/example.bin')
const fileSize = ref(0)
const fileSizeLoading = ref(false)
const fileSizeError = ref('')

const phase = ref<'idle' | 'fetching' | 'running' | 'done' | 'error'>('idle')
const globalError = ref('')
const aborted = ref(false)

type ChunkStatus = 'pending' | 'downloading' | 'done' | 'error'

interface ChunkState {
  index: number        // 分片序号，从 1 开始
  status: ChunkStatus
  start: number        // 起始字节
  end: number          // 结束字节
  retries: number
}

const chunks = ref<ChunkState[]>([])

// ── computed ───────────────────────────────────────────────────────────
const totalChunks = computed(() =>
    fileSize.value ? Math.ceil(fileSize.value / CHUNK_SIZE) : 0,
)
const doneCount = computed(() => chunks.value.filter(c => c.status === 'done').length)
const overallPct = computed(() =>
    totalChunks.value ? Math.round((doneCount.value / totalChunks.value) * 100) : 0,
)
const isTooSmall = computed(() =>
    fileSize.value > 0 && fileSize.value < SMALL_FILE_THRESHOLD,
)

// ── 辅助函数 ──────────────────────────────────────────────────────────
function sleep(ms: number) {
  return new Promise<void>(resolve => setTimeout(resolve, ms))
}

function reset() {
  chunks.value = []
  globalError.value = ''
  aborted.value = false
  phase.value = 'idle'
  fileSize.value = 0
  fileSizeError.value = ''
}

// 切换到普通下载
function switchToNormalDownload() {
  window.location.hash = '#/query/download'
}

// 获取文件大小
async function fetchFileSize(): Promise<boolean> {
  if (!downloadKey.value) return false

  fileSizeLoading.value = true
  fileSizeError.value = ''

  try {
    const res = await ossApi.query.getObject(downloadKey.value)
    // 这里依赖详情接口返回文件大小；若后端返回 null，说明对象不存在或无法获取元数据，
    // 必须立刻终止下载流程，避免后续分片计算基于错误前提继续执行。
    if (!res) {
      fileSizeError.value = '没有数据'
      return false
    }
    fileSize.value = res.size || 0
    return true
  } catch (e: unknown) {
    fileSizeError.value = e instanceof Error ? e.message : String(e)
    return false
  } finally {
    fileSizeLoading.value = false
  }
}

// 开始分片下载
async function startDownload() {
  if (!downloadKey.value || phase.value === 'running' || phase.value === 'fetching') return

  // 先获取文件大小
  phase.value = 'fetching'
  const ok = await fetchFileSize()

  if (!ok || fileSizeError.value) {
    phase.value = 'error'
    globalError.value = fileSizeError.value || '获取文件信息失败'
    return
  }

  // 检查是否是小文件
  if (isTooSmall.value) {
    // 小文件直接普通下载
    try {
      const res = await ossApi.query.download(downloadKey.value)
      const blob = res.data as Blob
      const fname = downloadKey.value.split('/').pop() ?? 'file'
      triggerDownload(blob, fname)
      phase.value = 'done'
    } catch (e: unknown) {
      phase.value = 'error'
      globalError.value = e instanceof Error ? e.message : String(e)
    }
    return
  }

  // 大文件分片下载
  const fSize = fileSize.value
  const total = Math.ceil(fSize / CHUNK_SIZE)

  // 重置并初始化分片（保留 fileSize）
  chunks.value = []
  globalError.value = ''
  aborted.value = false
  phase.value = 'running'

  // 初始化分片状态列表
  chunks.value = Array.from({length: total}, (_, i) => ({
    index: i + 1,
    status: 'pending' as ChunkStatus,
    start: i * CHUNK_SIZE,
    end: Math.min((i + 1) * CHUNK_SIZE - 1, fSize - 1),
    retries: 0,
  }))

  try {
    // 并发下载所有分片
    await downloadConcurrently()

    if (aborted.value) return

    // 下载完成后下载完整文件
    await mergeChunks()

    phase.value = 'done'
  } catch (e: unknown) {
    globalError.value = e instanceof Error ? e.message : String(e)
    phase.value = 'error'
  }
}

/**
 * 信号量并发控制下载
 */
async function downloadConcurrently() {
  let qi = 0 // 全局队列指针

  async function downloadOne(chunkIndex: number) {
    const state = chunks.value[chunkIndex - 1]
    state.status = 'downloading'

    for (let attempt = 0; attempt <= MAX_RETRY; attempt++) {
      try {
        await ossApi.query.downloadRange(downloadKey.value, state.start, state.end)
        state.status = 'done'
        return
      } catch (err) {
        if (attempt === MAX_RETRY || aborted.value) {
          state.status = 'error'
          globalError.value = `分片 ${chunkIndex} 下载失败（已重试 ${attempt} 次）`
          aborted.value = true
          throw err
        }
        state.retries++
        // 指数退避
        await sleep(500 * Math.pow(2, attempt))
      }
    }
  }

  // 工作线程函数：从队列取分片序号并下载
  async function worker() {
    while (!aborted.value) {
      const idx = ++qi
      if (idx > totalChunks.value) break
      await downloadOne(idx)
    }
  }

  // 启动 CONCURRENCY 个并发 worker
  const workers = Array.from(
      {length: Math.min(CONCURRENCY, totalChunks.value)},
      () => worker(),
  )
  await Promise.all(workers)
}

// 合并分片（下载完整文件）
async function mergeChunks() {
  aborted.value = true

  try {
    const res = await ossApi.query.download(downloadKey.value)
    const blob = res.data as Blob
    const fname = downloadKey.value.split('/').pop() ?? 'file'
    triggerDownload(blob, fname)
  } catch (e: unknown) {
    console.error('完整文件下载失败', e)
  }
}

function triggerDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  setTimeout(() => URL.revokeObjectURL(url), 5000)
}

function abort() {
  aborted.value = true
  phase.value = 'error'
  globalError.value = '已手动取消下载'
}

// 格式化文件大小
function formatSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1024 * 1024 * 1024) return (bytes / 1024 / 1024).toFixed(2) + ' MB'
  return (bytes / 1024 / 1024 / 1024).toFixed(2) + ' GB'
}
</script>

<template>
  <div>
    <h2 class="text-base font-semibold mb-1">分片下载</h2>
    <p class="text-sm text-[var(--color-muted)] mb-5">将大文件分片下载，最后合并成完整文件</p>

    <!-- 说明卡片 -->
    <div class="card mb-4 text-xs text-[var(--color-muted)] space-y-1">
      <p>• 分片大小：<code class="text-[var(--color-accent)]">5 MB</code> / 片</p>
      <p>• 并发下载：<code class="text-[var(--color-accent)]">{{ CONCURRENCY }}</code> 个分片同时下载，单片失败自动重试
        {{ MAX_RETRY }} 次</p>
      <p>• 建议文件大小：<code class="text-[var(--color-accent)]">&gt; 10 MB</code>，较小文件将自动使用普通下载</p>
    </div>

    <!-- 主流程 -->
    <ApiCard method="GET" path="/oss/object/download/**" summary="分片下载（大文件）">
      <!-- 文件输入 -->
      <div class="mb-4">
        <p class="section-label">objectName</p>
        <input v-model="downloadKey" class="oss-input" placeholder="demo/example.bin"
               :disabled="phase === 'running' || phase === 'fetching'"/>
      </div>

      <!-- 文件信息 -->
      <div v-if="fileSize > 0" class="mb-4">
        <div class="p-3 rounded bg-[var(--color-bg)] border border-[var(--color-border)]">
          <p>文件大小：<code class="text-[var(--color-accent)]">{{ formatSize(fileSize) }}</code></p>
          <p>将分为 <code class="text-[var(--color-accent)]">{{ totalChunks }}</code> 个分片下载</p>
        </div>

        <!-- 小文件提示 -->
        <div v-if="isTooSmall"
             class="mt-3 p-3 rounded border border-[var(--color-warning)] bg-[rgba(210,153,34,0.08)] text-[var(--color-warning)] text-xs">
          文件较小（&lt; 10MB），将自动使用普通下载
        </div>
      </div>

      <!-- 错误信息 -->
      <p v-if="fileSizeError" class="text-sm text-[var(--color-danger)] mb-3">{{ fileSizeError }}</p>
      <p v-if="globalError && phase === 'error'" class="text-sm text-[var(--color-danger)] mb-3">{{ globalError }}</p>

      <!-- 操作按钮 -->
      <div class="flex gap-2 flex-wrap mb-4">
        <button
            class="btn btn-primary"
            :disabled="!downloadKey || phase === 'running' || phase === 'fetching' || phase === 'done'"
            @click="startDownload"
        >
          <span v-if="phase === 'fetching' || phase === 'running'" class="spinner"/>
          {{
            phase === 'done' ? '✓ 下载完成' : phase === 'fetching' ? '获取文件信息…' : phase === 'running' ? '下载中…' : '开始下载'
          }}
        </button>
        <button v-if="phase === 'running'" class="btn btn-danger" @click="abort">取消</button>
        <button
            v-if="phase !== 'idle'"
            class="btn btn-ghost"
            @click="reset"
        >
          重置
        </button>
      </div>

      <!-- 进度展示 -->
      <div v-if="chunks.length" class="mb-4">
        <div class="flex items-center justify-between text-xs text-[var(--color-muted)] mb-1">
          <span>{{ doneCount }} / {{ totalChunks }} 分片已完成</span>
          <span>{{ overallPct }}%</span>
        </div>
        <div class="progress-track mb-3">
          <div class="progress-fill" :style="{ width: `${overallPct}%` }"/>
        </div>

        <!-- 分片状态网格 -->
        <div class="flex flex-wrap gap-1 mb-2">
          <span
              v-for="c in chunks"
              :key="c.index"
              :title="`分片 #${c.index} (${formatSize(c.end - c.start + 1)})${c.retries ? '（已重试 ' + c.retries + ' 次）' : ''}`"
              :class="[
              'inline-flex items-center justify-center w-6 h-6 rounded text-[10px] font-mono cursor-default',
              c.status === 'done'      ? 'bg-[var(--color-success)] text-[#0d1117]' :
              c.status === 'downloading' ? 'bg-[var(--color-accent)] text-[#0d1117] animate-pulse' :
              c.status === 'error'     ? 'bg-[var(--color-danger)] text-white' :
                                         'bg-[var(--color-border)] text-[var(--color-muted)]'
            ]"
          >{{ c.index }}</span>
        </div>
        <p class="text-[10px] text-[var(--color-muted)]">
          <span class="text-[var(--color-success)]">■</span> 完成 &nbsp;
          <span class="text-[var(--color-accent)]">■</span> 下载中 &nbsp;
          <span class="text-[var(--color-danger)]">■</span> 失败 &nbsp;
          <span class="text-[var(--color-border)]">■</span> 待下载
        </p>
      </div>
    </ApiCard>
  </div>
</template>
