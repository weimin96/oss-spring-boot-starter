<script setup lang="ts">
import { ref, computed } from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import ResultPanel from '@/components/ResultPanel.vue'
import { useResult } from '@/composables/useResult'
import { ossApi } from '@/api/oss'

const CHUNK_SIZE = 5 * 1024 * 1024 // 5 MB

// ── state ──────────────────────────────────────────────────────────────
const file = ref<File | null>(null)
const targetPath = ref('demo/')
const uploadId = ref('')
const objectName = ref('')
const chunks = ref<{ index: number; status: 'pending' | 'uploading' | 'done' | 'error' }[]>([])
const mergeResult = ref<unknown>(null)
const isDragOver = ref(false)
const globalError = ref('')

// Parts query
const partsObjectName = ref('')
const partsUploadId = ref('')
const { status: partsStatus, result: partsResult, error: partsError, execute: execParts } = useResult()

// Merge result panel
const { status: mergeStatus, result: mergeResultData, error: mergeError, execute: execMerge } = useResult()

// ── computed ───────────────────────────────────────────────────────────
const totalChunks = computed(() => (file.value ? Math.ceil(file.value.size / CHUNK_SIZE) : 0))
const uploadedCount = computed(() => chunks.value.filter(c => c.status === 'done').length)
const overallProgress = computed(() =>
  totalChunks.value ? Math.round((uploadedCount.value / totalChunks.value) * 100) : 0,
)
const phase = ref<'idle' | 'initialized' | 'uploading' | 'merged'>('idle')

// ── methods ────────────────────────────────────────────────────────────
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
  objectName.value = ''
  mergeResult.value = null
  globalError.value = ''
  phase.value = 'idle'
}

/** Step 1: init */
async function initTask() {
  if (!file.value) return
  globalError.value = ''
  try {
    const chunkNum = totalChunks.value
    const id = await ossApi.multipart.init({
      filename: file.value.name,
      path: targetPath.value,
      size: file.value.size,
      chunkSize: CHUNK_SIZE,
      chunkNum,
    })
    uploadId.value = id
    objectName.value = `${targetPath.value}${file.value.name}`
    chunks.value = Array.from({ length: chunkNum }, (_, i) => ({ index: i + 1, status: 'pending' }))
    phase.value = 'initialized'
  } catch (e: unknown) {
    globalError.value = e instanceof Error ? e.message : String(e)
  }
}

/** Step 2: upload all chunks sequentially (could be parallelized) */
async function uploadChunks() {
  if (!file.value || !uploadId.value) return
  phase.value = 'uploading'
  for (const chunk of chunks.value) {
    if (chunk.status === 'done') continue
    chunk.status = 'uploading'
    const start = (chunk.index - 1) * CHUNK_SIZE
    const blob = file.value.slice(start, start + CHUNK_SIZE)
    try {
      await ossApi.multipart.uploadChunk(blob, uploadId.value, objectName.value, chunk.index)
      chunk.status = 'done'
    } catch {
      chunk.status = 'error'
      globalError.value = `分片 ${chunk.index} 上传失败`
      phase.value = 'initialized'
      return
    }
  }
}

/** Step 3: merge */
async function mergeChunks() {
  await execMerge(() =>
    ossApi.multipart.merge({
      uploadId: uploadId.value,
      objectName: objectName.value,
      filename: file.value!.name,
    }),
  )
  if (mergeStatus.value === 'success') phase.value = 'merged'
}
</script>

<template>
  <div>
    <h2 class="text-base font-semibold mb-1">分片上传</h2>
    <p class="text-sm text-[var(--color-muted)] mb-5">支持大文件断点续传：Init → Chunk × N → Merge</p>

    <!-- 主流程 -->
    <div class="card mb-4">
      <div class="flex items-center gap-3 mb-4">
        <span class="method method-POST">POST</span>
        <code class="text-xs text-[var(--color-muted)]">/oss/multipart/{init · chunk · merge}</code>
        <p class="text-sm font-medium">分片上传完整流程</p>
      </div>

      <!-- File picker -->
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
            {{ (file.size / 1024 / 1024).toFixed(2) }} MB — {{ totalChunks }} 个分片（每片 5 MB）
          </p>
        </div>
        <p v-else class="text-sm">点击选择文件，或拖拽到此处（建议 &gt;5 MB）</p>
      </div>

      <div class="mb-4">
        <p class="section-label">存放路径</p>
        <input v-model="targetPath" class="oss-input" placeholder="demo/" />
      </div>

      <!-- Step indicators -->
      <div class="flex gap-2 mb-5">
        <span :class="['badge', phase !== 'idle' ? 'badge-success' : 'badge-info']">① 初始化</span>
        <span class="text-[var(--color-muted)] text-xs self-center">→</span>
        <span :class="['badge', uploadedCount > 0 ? 'badge-success' : 'badge-info']">② 分片上传 {{ uploadedCount }}/{{ totalChunks }}</span>
        <span class="text-[var(--color-muted)] text-xs self-center">→</span>
        <span :class="['badge', phase === 'merged' ? 'badge-success' : 'badge-info']">③ 合并</span>
      </div>

      <!-- Action buttons -->
      <div class="flex gap-2 flex-wrap mb-4">
        <button class="btn btn-ghost" :disabled="!file || phase !== 'idle'" @click="initTask">① 初始化任务</button>
        <button class="btn btn-ghost" :disabled="phase !== 'initialized' && phase !== 'uploading'" @click="uploadChunks">② 上传所有分片</button>
        <button class="btn btn-primary" :disabled="uploadedCount !== totalChunks || phase === 'merged'" @click="mergeChunks">③ 合并分片</button>
        <button class="btn btn-ghost" @click="reset">重置</button>
      </div>

      <!-- Global error -->
      <p v-if="globalError" class="text-sm text-[var(--color-danger)] mb-3">{{ globalError }}</p>

      <!-- uploadId display -->
      <div v-if="uploadId" class="mb-3">
        <p class="section-label">uploadId</p>
        <code class="text-xs text-[var(--color-accent)] break-all">{{ uploadId }}</code>
      </div>

      <!-- Progress -->
      <div v-if="chunks.length" class="mb-4">
        <div class="progress-track mb-2">
          <div class="progress-fill" :style="{ width: `${overallProgress}%` }" />
        </div>
        <div class="flex flex-wrap gap-1">
          <span
            v-for="c in chunks"
            :key="c.index"
            :class="[
              'inline-block w-6 h-6 rounded text-center text-[10px] leading-6 font-mono',
              c.status === 'done'     ? 'bg-[var(--color-success)] text-[#0d1117]' :
              c.status === 'uploading'? 'bg-[var(--color-accent)] text-[#0d1117]' :
              c.status === 'error'    ? 'bg-[var(--color-danger)] text-white' :
                                        'bg-[var(--color-border)] text-[var(--color-muted)]'
            ]"
          >{{ c.index }}</span>
        </div>
        <p class="text-xs text-[var(--color-muted)] mt-1">
          绿=已完成 蓝=上传中 红=失败 灰=待上传
        </p>
      </div>

      <ResultPanel :status="mergeStatus" :result="mergeResultData" :error="mergeError" label="合并结果 ObjectInfo" />
    </div>

    <!-- 查询已上传分片 -->
    <ApiCard method="GET" path="/oss/multipart/parts" summary="查询已上传的分片列表（断点续传）">
      <div class="grid grid-cols-2 gap-3 mb-4">
        <div>
          <p class="section-label">objectName</p>
          <input v-model="partsObjectName" class="oss-input" :placeholder="objectName || 'demo/file.zip'" />
        </div>
        <div>
          <p class="section-label">uploadId</p>
          <input v-model="partsUploadId" class="oss-input" :placeholder="uploadId || 'xxxx-xxxx'" />
        </div>
      </div>
      <button
        class="btn btn-ghost"
        :disabled="partsStatus === 'loading'"
        @click="execParts(() => ossApi.multipart.listParts(partsObjectName || objectName, partsUploadId || uploadId))"
      >
        <span v-if="partsStatus === 'loading'" class="spinner" />
        <span>查询分片</span>
      </button>
      <ResultPanel :status="partsStatus" :result="partsResult" :error="partsError" label="已上传分片列表" />
    </ApiCard>
  </div>
</template>
