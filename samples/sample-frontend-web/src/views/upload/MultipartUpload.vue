<script setup lang="ts">
/**
 * 分片上传 Demo
 *
 * 设计要点：
 * 1. 全自动流程：选文件后点「开始上传」，内部自动 Init → Chunk×N 并发 → Merge
 * 2. 小文件保护：< MULTIPART_THRESHOLD (10MB) 时显示警告，建议直传
 * 3. 分布式兼容：uploadId 由 OSS 服务端持有（S3 协议保证），
 *    每个分片是独立 HTTP 请求，可经负载均衡路由到不同后端实例，互不影响
 * 4. FormData 正确性：file 字段附带 filename，chunkNumber/filename/path/guid/uploadId 同表单传递
 * 5. 并发控制：CONCURRENCY 个分片同时上传，单片失败自动重试 MAX_RETRY 次
 * 6. 断点续传：可通过「查询已上传分片」获取已完成列表，手动跳过重传
 */
import { ref, computed } from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import ResultPanel from '@/components/ResultPanel.vue'
import { useResult } from '@/composables/useResult'
import { ossApi } from '@/api/oss'
import type { ChunkTarget } from '@/types'

// ── 常量配置 ──────────────────────────────────────────────────────────
const CHUNK_SIZE = 5 * 1024 * 1024            // 5 MB / 片（S3 最小单片要求）
const MULTIPART_THRESHOLD = 10 * 1024 * 1024  // < 10 MB 建议直接上传
const CONCURRENCY = 3                          // 同时上传的分片数
const MAX_RETRY = 2                            // 单片失败最大重试次数

// ── 状态 ──────────────────────────────────────────────────────────────
const file = ref<File | null>(null)
const targetPath = ref('demo/')
const isDragOver = ref(false)

type ChunkStatus = 'pending' | 'uploading' | 'done' | 'error'
interface ChunkState {
  index: number        // 分片序号，从 1 开始（= chunkNumber）
  status: ChunkStatus
  target?: ChunkTarget // 成功后写入 { partNumber, etag }
  retries: number
}

const chunks = ref<ChunkState[]>([])
const uploadId = ref('')
const guid = ref('')
const phase = ref<'idle' | 'running' | 'done' | 'error'>('idle')
const globalError = ref('')
const aborted = ref(false)

const { status: mergeStatus, result: mergeResult, error: mergeError, execute: execMerge } = useResult()

// 已上传分片查询（断点续传辅助）
const partsObjectName = ref('')
const partsUploadId = ref('')
const { status: partsStatus, result: partsResult, error: partsError, execute: execParts } = useResult()

// ── computed ───────────────────────────────────────────────────────────
const totalChunks = computed(() =>
  file.value ? Math.ceil(file.value.size / CHUNK_SIZE) : 0,
)
const doneCount = computed(() => chunks.value.filter(c => c.status === 'done').length)
const overallPct = computed(() =>
  totalChunks.value ? Math.round((doneCount.value / totalChunks.value) * 100) : 0,
)
const isTooSmall = computed(() =>
  file.value !== null && file.value.size < MULTIPART_THRESHOLD,
)
const objectName = computed(() =>
  file.value ? `${targetPath.value.replace(/\/$/, '')}/${file.value.name}` : '',
)

// ── 辅助 ──────────────────────────────────────────────────────────────
function onFileChange(e: Event) {
  file.value = (e.target as HTMLInputElement).files?.[0] ?? null
  reset()
}
function onDrop(e: DragEvent) {
  isDragOver.value = false
  file.value = e.dataTransfer?.files[0] ?? null
  reset()
}
function reset() {
  chunks.value = []
  uploadId.value = ''
  guid.value = ''
  globalError.value = ''
  aborted.value = false
  phase.value = 'idle'
}

/** 生成 UUID v4（无需第三方依赖） */
function genUuid(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16)
  })
}

function sleep(ms: number) {
  return new Promise<void>(resolve => setTimeout(resolve, ms))
}

// ── 主流程：一键全自动上传 ────────────────────────────────────────────
async function startUpload() {
  if (!file.value || phase.value === 'running') return
  reset()
  phase.value = 'running'

  const f = file.value
  const path = targetPath.value
  const filename = f.name
  const total = Math.ceil(f.size / CHUNK_SIZE)

  // 初始化分片状态列表
  chunks.value = Array.from({ length: total }, (_, i) => ({
    index: i + 1,
    status: 'pending' as ChunkStatus,
    retries: 0,
  }))

  try {
    // ── Step 1: 初始化任务（仅一次，uploadId 由 OSS/S3 服务端生成）
    // 分布式说明：uploadId 存储在 OSS 服务端，应用层无状态，
    // 后续各分片请求可路由到任意后端实例
    const id = await ossApi.multipart.init(filename, path)
    uploadId.value = id
    guid.value = genUuid()

    // ── Step 2: 并发上传所有分片
    await uploadConcurrently(f, filename, path, id, guid.value, total)

    if (aborted.value) return

    // ── Step 3: 合并分片
    const targetList: ChunkTarget[] = chunks.value
      .filter(c => c.status === 'done' && c.target)
      .sort((a, b) => a.index - b.index)
      .map(c => c.target!)

    await execMerge(() =>
      ossApi.multipart.merge(filename, path, id, guid.value, targetList),
    )

    phase.value = mergeStatus.value === 'success' ? 'done' : 'error'
  } catch (e: unknown) {
    globalError.value = e instanceof Error ? e.message : String(e)
    phase.value = 'error'
  }
}

/**
 * 信号量并发控制上传
 * - 每个分片独立请求，可路由到不同后端实例（分布式友好）
 * - 失败自动指数退避重试
 */
async function uploadConcurrently(
  f: File,
  filename: string,
  path: string,
  uid: string,
  guidVal: string,
  total: number,
) {
  let qi = 0 // 全局队列指针（原子递增，无竞态）

  async function uploadOne(chunkIndex: number) {
    const state = chunks.value[chunkIndex - 1]
    state.status = 'uploading'

    const start = (chunkIndex - 1) * CHUNK_SIZE
    const end = Math.min(start + CHUNK_SIZE, f.size)
    // slice 出精确字节范围，避免最后一片超出文件末尾
    const blob = f.slice(start, end)

    for (let attempt = 0; attempt <= MAX_RETRY; attempt++) {
      try {
        // 每次请求独立 FormData，互不影响
        const target = await ossApi.multipart.uploadChunk(
          blob, chunkIndex, filename, path, guidVal, uid,
        )
        state.status = 'done'
        state.target = target
        return
      } catch (err) {
        if (attempt === MAX_RETRY || aborted.value) {
          state.status = 'error'
          globalError.value = `分片 ${chunkIndex} 上传失败（已重试 ${attempt} 次）`
          aborted.value = true
          throw err
        }
        state.retries++
        // 指数退避：500ms, 1000ms
        await sleep(500 * Math.pow(2, attempt))
      }
    }
  }

  // 工作线程函数：从队列取分片序号并上传
  async function worker() {
    while (!aborted.value) {
      // 原子取队列位置（JS 单线程，无需锁）
      const idx = ++qi
      if (idx > total) break
      await uploadOne(idx)
    }
  }

  // 启动 CONCURRENCY 个并发 worker
  const workers = Array.from(
    { length: Math.min(CONCURRENCY, total) },
    () => worker(),
  )
  await Promise.all(workers)
}

function abort() {
  aborted.value = true
  phase.value = 'error'
  globalError.value = '已手动取消上传'
}
</script>

<template>
  <div>
    <h2 class="text-base font-semibold mb-1">分片上传</h2>
    <p class="text-sm text-[var(--color-muted)] mb-5">
      全自动流程（Init → Chunk×N 并发 → Merge），兼容分布式部署，支持失败重试
    </p>

    <!-- 说明卡片 -->
    <div class="card mb-4 text-xs text-[var(--color-muted)] space-y-1">
      <p>• 分片大小：<code class="text-[var(--color-accent)]">5 MB</code>（S3 协议最小单片要求）</p>
      <p>• 并发上传：<code class="text-[var(--color-accent)]">{{ 3 }}</code> 个分片同时上传，单片失败自动重试 {{ MAX_RETRY }} 次</p>
      <p>• 建议文件大小：<code class="text-[var(--color-accent)]">&gt; 10 MB</code>，较小文件请用「单文件上传」</p>
      <p>
        • 分布式兼容：<code class="text-[var(--color-accent)]">uploadId</code> 由 S3/OSS 服务端持有，
        应用层无状态，各分片请求可路由到任意后端实例
      </p>
    </div>

    <!-- 主流程 -->
    <div class="card mb-4">
      <div class="flex items-center gap-3 mb-4">
        <span class="method method-POST">POST</span>
        <code class="text-xs text-[var(--color-muted)]">/oss/multipart/{init · chunk · merge}</code>
        <p class="text-sm font-medium">全自动分片上传</p>
      </div>

      <!-- 文件拖拽区 -->
      <div
        class="upload-zone mb-4"
        :class="{ 'drag-over': isDragOver }"
        @dragover.prevent="isDragOver = true"
        @dragleave="isDragOver = false"
        @drop.prevent="onDrop"
        @click="($refs.fileInput as HTMLInputElement).click()"
      >
        <input ref="fileInput" type="file" class="hidden" @change="onFileChange" />
        <div v-if="file">
          <p class="text-sm font-medium text-[var(--color-text)]">{{ file.name }}</p>
          <p class="text-xs text-[var(--color-muted)] mt-1">
            {{ (file.size / 1024 / 1024).toFixed(2) }} MB — 将分为 {{ totalChunks }} 片上传
          </p>
        </div>
        <p v-else class="text-sm">点击选择文件，或拖拽到此处（建议 &gt; 10 MB）</p>
      </div>

      <!-- 文件过小警告 -->
      <div
        v-if="isTooSmall"
        class="p-3 mb-3 rounded border border-[var(--color-warning)] bg-[rgba(210,153,34,0.08)] text-[var(--color-warning)] text-xs"
      >
        ⚠️ 文件仅 {{ (file!.size / 1024).toFixed(0) }} KB，分片上传有额外初始化开销，
        建议改用「<router-link to="/upload/simple" class="underline">单文件上传</router-link>」。
        如需继续可强制上传。
      </div>

      <!-- 存放路径 -->
      <div class="mb-4">
        <p class="section-label">存放路径 path</p>
        <input
          v-model="targetPath"
          class="oss-input"
          placeholder="demo/"
          :disabled="phase === 'running'"
        />
      </div>

      <!-- 操作按钮 -->
      <div class="flex gap-2 flex-wrap mb-4">
        <button
          class="btn btn-primary"
          :disabled="!file || phase === 'running' || phase === 'done'"
          @click="startUpload"
        >
          <span v-if="phase === 'running'" class="spinner" />
          {{ phase === 'done' ? '✓ 上传完成' : phase === 'running' ? '上传中…' : '开始上传' }}
        </button>
        <button v-if="phase === 'running'" class="btn btn-danger" @click="abort">取消</button>
        <button
          v-if="phase !== 'idle'"
          class="btn btn-ghost"
          @click="() => { reset(); (($refs.fileInput as HTMLInputElement).value = ''); file = null }"
        >
          重置
        </button>
      </div>

      <!-- 全局错误 -->
      <p v-if="globalError" class="text-sm text-[var(--color-danger)] mb-3">{{ globalError }}</p>

      <!-- uploadId 显示 -->
      <div v-if="uploadId" class="mb-3">
        <p class="section-label">uploadId（OSS 服务端生成，与应用实例无关）</p>
        <code class="text-xs text-[var(--color-accent)] break-all">{{ uploadId }}</code>
      </div>

      <!-- 进度展示 -->
      <div v-if="chunks.length" class="mb-4">
        <div class="flex items-center justify-between text-xs text-[var(--color-muted)] mb-1">
          <span>{{ doneCount }} / {{ totalChunks }} 分片已完成</span>
          <span>{{ overallPct }}%</span>
        </div>
        <div class="progress-track mb-3">
          <div class="progress-fill" :style="{ width: `${overallPct}%` }" />
        </div>

        <!-- 分片状态网格（每片一格，hover 可看 index/重试次数） -->
        <div class="flex flex-wrap gap-1 mb-2">
          <span
            v-for="c in chunks"
            :key="c.index"
            :title="`分片 #${c.index}${c.retries ? '（已重试 ' + c.retries + ' 次）' : ''}`"
            :class="[
              'inline-flex items-center justify-center w-6 h-6 rounded text-[10px] font-mono cursor-default',
              c.status === 'done'      ? 'bg-[var(--color-success)] text-[#0d1117]' :
              c.status === 'uploading' ? 'bg-[var(--color-accent)] text-[#0d1117] animate-pulse' :
              c.status === 'error'     ? 'bg-[var(--color-danger)] text-white' :
                                         'bg-[var(--color-border)] text-[var(--color-muted)]'
            ]"
          >{{ c.index }}</span>
        </div>
        <p class="text-[10px] text-[var(--color-muted)]">
          <span class="text-[var(--color-success)]">■</span> 完成 &nbsp;
          <span class="text-[var(--color-accent)]">■</span> 上传中 &nbsp;
          <span class="text-[var(--color-danger)]">■</span> 失败 &nbsp;
          <span class="text-[var(--color-border)]">■</span> 待上传
        </p>
      </div>

      <!-- 合并结果 -->
      <ResultPanel
        :status="mergeStatus"
        :result="mergeResult"
        :error="mergeError"
        label="合并结果 ObjectInfo"
      />
    </div>

    <!-- 查询已上传分片 -->
    <ApiCard method="GET" path="/oss/multipart/parts" summary="查询已上传的分片列表（断点续传参考）">
      <div class="grid grid-cols-2 gap-3 mb-4">
        <div>
          <p class="section-label">objectName</p>
          <input
            v-model="partsObjectName"
            class="oss-input"
            :placeholder="objectName || 'demo/file.zip'"
          />
        </div>
        <div>
          <p class="section-label">uploadId</p>
          <input
            v-model="partsUploadId"
            class="oss-input"
            :placeholder="uploadId || 'xxxx-xxxx'"
          />
        </div>
      </div>
      <button
        class="btn btn-ghost"
        :disabled="partsStatus === 'loading'"
        @click="execParts(() => ossApi.multipart.listParts(
          partsObjectName || objectName,
          partsUploadId || uploadId,
        ))"
      >
        <span v-if="partsStatus === 'loading'" class="spinner" />查询已上传分片
      </button>
      <ResultPanel
        :status="partsStatus"
        :result="partsResult"
        :error="partsError"
        label="已上传分片列表（可判断哪些分片已完成，用于断点续传）"
      />
    </ApiCard>
  </div>
</template>
