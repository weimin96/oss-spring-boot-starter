<script setup lang="ts">
import { computed, ref } from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import ResultPanel from '@/components/ResultPanel.vue'
import { useResult } from '@/composables/useResult'
import { ossApi } from '@/api/oss'
import type { ChunkTarget } from '@/types'

const CHUNK_SIZE = 5 * 1024 * 1024
const MULTIPART_THRESHOLD = 10 * 1024 * 1024
const MAX_PARALLEL_CHUNKS = 3
const MAX_RETRY = 2

type UploadPhase = 'idle' | 'initializing' | 'uploading' | 'merging' | 'done' | 'error'
type ChunkStatus = 'pending' | 'uploading' | 'done' | 'error'

interface ChunkDescriptor {
  index: number
  start: number
  end: number
  size: number
}

interface ChunkState extends ChunkDescriptor {
  status: ChunkStatus
  retries: number
  target?: ChunkTarget
  errorMessage?: string
}

interface UploadContext {
  file: File
  filename: string
  path: string
  uploadId: string
  guid: string
  signal: AbortSignal
}

const fileInputRef = ref<HTMLInputElement | null>(null)
const selectedFile = ref<File | null>(null)
const targetPath = ref('demo/')
const isDragOver = ref(false)

const uploadId = ref('')
const guid = ref('')
const uploadPhase = ref<UploadPhase>('idle')
const uploadMessage = ref('')
const chunkStates = ref<ChunkState[]>([])
const uploadController = ref<AbortController | null>(null)

const { status: mergeStatus, result: mergeResult, error: mergeError, execute: execMerge, reset: resetMerge } = useResult()

const partsObjectName = ref('')
const partsUploadId = ref('')
const { status: partsStatus, result: partsResult, error: partsError, execute: execParts, reset: resetParts } = useResult()

const totalChunks = computed(() =>
  chunkStates.value.length || (selectedFile.value ? Math.ceil(selectedFile.value.size / CHUNK_SIZE) : 0),
)
const completedChunks = computed(() => chunkStates.value.filter(chunk => chunk.status === 'done').length)
const activeChunks = computed(() => chunkStates.value.filter(chunk => chunk.status === 'uploading').length)
const overallProgress = computed(() =>
  totalChunks.value ? Math.round((completedChunks.value / totalChunks.value) * 100) : 0,
)
const isUploading = computed(() =>
  uploadPhase.value === 'initializing' || uploadPhase.value === 'uploading' || uploadPhase.value === 'merging',
)
const isSmallFile = computed(() =>
  selectedFile.value !== null && selectedFile.value.size < MULTIPART_THRESHOLD,
)
const resolvedObjectName = computed(() => {
  if (!selectedFile.value) {
    return ''
  }
  return buildObjectName(targetPath.value, selectedFile.value.name)
})
const selectedFileSizeText = computed(() => formatFileSize(selectedFile.value?.size ?? 0))
const phaseLabelMap: Record<UploadPhase, string> = {
  idle: '待开始',
  initializing: '初始化上传任务',
  uploading: '上传分片',
  merging: '合并分片',
  done: '上传完成',
  error: '上传失败',
}

function openFilePicker() {
  fileInputRef.value?.click()
}

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  applySelectedFile(input.files?.[0] ?? null)
}

function onDrop(event: DragEvent) {
  isDragOver.value = false
  applySelectedFile(event.dataTransfer?.files[0] ?? null)
}

function applySelectedFile(file: File | null) {
  selectedFile.value = file
  resetUploadRuntimeState()
}

function clearSelectedFile() {
  resetUploadRuntimeState()
  selectedFile.value = null
  if (fileInputRef.value) {
    fileInputRef.value.value = ''
  }
}

function resetUploadRuntimeState() {
  if (uploadController.value) {
    uploadController.value.abort()
    uploadController.value = null
  }
  uploadId.value = ''
  guid.value = ''
  uploadPhase.value = 'idle'
  uploadMessage.value = ''
  chunkStates.value = []
  resetMerge()
}

function normalizePath(path: string): string {
  const trimmedPath = path.trim().replace(/\\/g, '/').replace(/^\/+/, '')
  if (!trimmedPath) {
    return ''
  }
  return trimmedPath.endsWith('/') ? trimmedPath : `${trimmedPath}/`
}

function buildObjectName(path: string, filename: string): string {
  const normalizedPath = normalizePath(path)
  return normalizedPath ? `${normalizedPath}${filename}` : filename
}

function formatFileSize(size: number): string {
  if (size >= 1024 * 1024 * 1024) {
    return `${(size / 1024 / 1024 / 1024).toFixed(2)} GB`
  }
  if (size >= 1024 * 1024) {
    return `${(size / 1024 / 1024).toFixed(2)} MB`
  }
  if (size >= 1024) {
    return `${(size / 1024).toFixed(2)} KB`
  }
  return `${size} B`
}

function createGuid(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (char) => {
    const randomValue = (Math.random() * 16) | 0
    const segment = char === 'x' ? randomValue : (randomValue & 0x3) | 0x8
    return segment.toString(16)
  })
}

function createAbortError(): Error {
  const error = new Error('已取消上传') as Error & { name?: string; code?: string }
  error.name = 'AbortError'
  error.code = 'ERR_CANCELED'
  return error
}

function isAbortError(error: unknown): boolean {
  if (!error || typeof error !== 'object') {
    return false
  }
  const abortName = 'name' in error ? String(error.name) : ''
  const abortCode = 'code' in error ? String(error.code) : ''
  return abortName === 'AbortError' || abortCode === 'ERR_CANCELED'
}

function resolveErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error)
}

function createChunkStates(file: File): ChunkState[] {
  const total = Math.ceil(file.size / CHUNK_SIZE)
  if (total <= 0) {
    throw new Error('空文件不支持分片上传')
  }

  return Array.from({ length: total }, (_, index) => {
    const start = index * CHUNK_SIZE
    const end = Math.min(start + CHUNK_SIZE, file.size)
    const size = end - start

    if (size <= 0) {
      throw new Error(`分片 ${index + 1} 计算结果异常，已阻止空分片上传`)
    }

    return {
      index: index + 1,
      start,
      end,
      size,
      status: 'pending' as const,
      retries: 0,
    }
  })
}

/**
 * 每次上传前重新按范围切出 `File`，避免页面层直接操作 FormData，
 * 也确保每个分片都携带稳定的文件名和非空内容。
 */
function createChunkFile(sourceFile: File, chunk: ChunkDescriptor): File {
  const chunkBlob = sourceFile.slice(chunk.start, chunk.end)
  if (chunkBlob.size <= 0) {
    throw new Error(`分片 ${chunk.index} 内容为空，已阻止上传`)
  }

  return new File(
    [chunkBlob],
    `${sourceFile.name}.part-${String(chunk.index).padStart(4, '0')}`,
    { type: sourceFile.type || 'application/octet-stream' },
  )
}

function buildChunkTooltip(chunk: ChunkState): string {
  const lines = [
    `分片 #${chunk.index}`,
    `字节范围：${chunk.start} - ${chunk.end - 1}`,
    `分片大小：${formatFileSize(chunk.size)}`,
  ]

  if (chunk.retries > 0) {
    lines.push(`已重试：${chunk.retries} 次`)
  }

  if (chunk.errorMessage) {
    lines.push(`最后错误：${chunk.errorMessage}`)
  }

  return lines.join('\n')
}

function buildUploadContext(signal: AbortSignal): UploadContext {
  if (!selectedFile.value) {
    throw new Error('请先选择需要上传的文件')
  }

  const normalizedPath = normalizePath(targetPath.value)
  if (!normalizedPath) {
    throw new Error('请输入存放路径 path')
  }

  return {
    file: selectedFile.value,
    filename: selectedFile.value.name,
    path: normalizedPath,
    uploadId: '',
    guid: '',
    signal,
  }
}

function collectUploadedChunkTargets(): ChunkTarget[] {
  const incompleteChunks = chunkStates.value.filter(chunk => chunk.status !== 'done' || !chunk.target)
  if (incompleteChunks.length > 0) {
    const indexes = incompleteChunks.map(chunk => chunk.index).join('、')
    throw new Error(`仍有分片未完成，无法合并。未完成分片：${indexes}`)
  }

  return chunkStates.value
    .slice()
    .sort((left, right) => left.index - right.index)
    .map(chunk => chunk.target!)
}

function waitForRetry(delayMs: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal.aborted) {
      reject(createAbortError())
      return
    }

    const onAbort = () => {
      window.clearTimeout(timer)
      reject(createAbortError())
    }
    const timer = window.setTimeout(() => {
      signal.removeEventListener('abort', onAbort)
      resolve()
    }, delayMs)

    signal.addEventListener('abort', onAbort, { once: true })
  })
}

async function uploadSingleChunk(context: UploadContext, chunk: ChunkState) {
  for (let attempt = 0; attempt <= MAX_RETRY; attempt++) {
    if (context.signal.aborted) {
      throw createAbortError()
    }

    chunk.status = 'uploading'
    chunk.errorMessage = undefined

    try {
      const chunkFile = createChunkFile(context.file, chunk)
      const target = await ossApi.multipart.uploadChunk({
        file: chunkFile,
        chunkNumber: chunk.index,
        filename: context.filename,
        path: context.path,
        guid: context.guid,
        uploadId: context.uploadId,
        signal: context.signal,
      })

      chunk.status = 'done'
      chunk.target = target
      return
    } catch (error) {
      if (isAbortError(error) || context.signal.aborted) {
        chunk.status = 'pending'
        throw createAbortError()
      }

      chunk.retries = attempt + 1
      chunk.errorMessage = resolveErrorMessage(error)

      if (attempt === MAX_RETRY) {
        chunk.status = 'error'
        throw new Error(`分片 ${chunk.index} 上传失败：${chunk.errorMessage}`)
      }

      chunk.status = 'pending'
      await waitForRetry(500 * Math.pow(2, attempt), context.signal)
    }
  }
}

/**
 * 通过共享游标分发待上传分片，逻辑简单，也方便把这段代码直接迁移到其他项目。
 */
async function uploadChunksInParallel(context: UploadContext) {
  let cursor = 0

  async function worker() {
    while (cursor < chunkStates.value.length) {
      const currentIndex = cursor
      cursor += 1

      const chunk = chunkStates.value[currentIndex]
      if (!chunk) {
        return
      }

      await uploadSingleChunk(context, chunk)
    }
  }

  const workerCount = Math.min(MAX_PARALLEL_CHUNKS, chunkStates.value.length)
  const results = await Promise.allSettled(
    Array.from({ length: workerCount }, () => worker()),
  )

  const failed = results.find((result): result is PromiseRejectedResult => result.status === 'rejected')
  if (failed) {
    throw failed.reason
  }
}

async function startUpload() {
  if (isUploading.value) {
    return
  }

  try {
    resetUploadRuntimeState()
    resetParts()

    const controller = new AbortController()
    uploadController.value = controller

    const context = buildUploadContext(controller.signal)
    chunkStates.value = createChunkStates(context.file)
    uploadMessage.value = ''
    uploadPhase.value = 'initializing'

    context.guid = createGuid()
    guid.value = context.guid

    context.uploadId = await ossApi.multipart.init({
      filename: context.filename,
      path: context.path,
      signal: context.signal,
    })
    uploadId.value = context.uploadId

    partsObjectName.value = buildObjectName(context.path, context.filename)
    partsUploadId.value = context.uploadId

    uploadPhase.value = 'uploading'
    await uploadChunksInParallel(context)

    const chunkTargetList = collectUploadedChunkTargets()

    uploadPhase.value = 'merging'
    await execMerge(() => ossApi.multipart.merge({
      filename: context.filename,
      path: context.path,
      uploadId: context.uploadId,
      guid: context.guid,
      chunkTargetList,
      signal: context.signal,
    }))

    if (mergeStatus.value === 'success') {
      uploadPhase.value = 'done'
      uploadMessage.value = ''
      return
    }

    uploadPhase.value = 'error'
    uploadMessage.value = mergeError.value ?? '分片合并失败'
  } catch (error) {
    uploadPhase.value = 'error'
    uploadMessage.value = isAbortError(error) ? '已取消上传' : resolveErrorMessage(error)
  } finally {
    uploadController.value = null
  }
}

function abortUpload() {
  if (!uploadController.value) {
    return
  }

  uploadController.value.abort()
  uploadPhase.value = 'error'
  uploadMessage.value = '已取消上传'
}

async function queryUploadedParts() {
  const objectName = partsObjectName.value.trim() || resolvedObjectName.value
  const currentUploadId = partsUploadId.value.trim() || uploadId.value

  partsObjectName.value = objectName
  partsUploadId.value = currentUploadId

  await execParts(() => {
    if (!objectName || !currentUploadId) {
      return Promise.reject(new Error('请先提供 objectName 和 uploadId'))
    }

    return ossApi.multipart.listParts(objectName, currentUploadId)
  })
}
</script>

<template>
  <div>
    <h2 class="text-base font-semibold mb-1">分片上传</h2>
    <p class="text-sm text-[var(--color-muted)] mb-5">
      演示从初始化任务、并发上传分片到合并对象的完整流程，适合作为业务项目的基础实现参考。
    </p>

    <ApiCard method="POST" path="/oss/multipart/{init,chunk,merge}" summary="全自动分片上传">
      <div class="mb-4 grid grid-cols-1 md:grid-cols-2 gap-3 text-xs text-[var(--color-muted)]">
        <div class="rounded border border-[var(--color-border)] p-3 space-y-1">
          <p>分片大小：<code class="text-[var(--color-accent)]">5 MB</code></p>
          <p>并发数：<code class="text-[var(--color-accent)]">{{ MAX_PARALLEL_CHUNKS }}</code></p>
          <p>单片最大重试：<code class="text-[var(--color-accent)]">{{ MAX_RETRY }}</code> 次</p>
        </div>
        <div class="rounded border border-[var(--color-border)] p-3 space-y-1">
          <p>空分片防御：发送前校验分片大小，阻止空 `FormData` 请求</p>
          <p>取消能力：通过 `AbortController` 中断正在进行的请求</p>
          <p>合并保护：只有全部分片成功返回 `etag` 才允许执行 merge</p>
        </div>
      </div>

      <div
        class="upload-zone mb-4"
        :class="{ 'drag-over': isDragOver }"
        @dragover.prevent="isDragOver = true"
        @dragleave="isDragOver = false"
        @drop.prevent="onDrop"
        @click="openFilePicker"
      >
        <input ref="fileInputRef" type="file" class="hidden" @change="onFileChange" />
        <div v-if="selectedFile">
          <p class="text-sm font-medium text-[var(--color-text)]">{{ selectedFile.name }}</p>
          <p class="text-xs text-[var(--color-muted)] mt-1">
            {{ selectedFileSizeText }}，预计拆分为 {{ totalChunks }} 个分片
          </p>
        </div>
        <div v-else>
          <p class="text-sm">点击选择文件，或拖拽到此处</p>
          <p class="text-xs mt-1 opacity-60">建议选择大于 10 MB 的文件验证分片流程</p>
        </div>
      </div>

      <div v-if="selectedFile" class="grid grid-cols-1 md:grid-cols-2 gap-3 mb-4">
        <div class="rounded border border-[var(--color-border)] p-3">
          <p class="section-label">文件信息</p>
          <p class="text-sm mt-1">文件大小：{{ selectedFileSizeText }}</p>
          <p class="text-sm mt-1">分片数量：{{ totalChunks }}</p>
          <p class="text-sm mt-1">对象 Key：{{ resolvedObjectName }}</p>
        </div>
        <div class="rounded border border-[var(--color-border)] p-3">
          <p class="section-label">上传状态</p>
          <p class="text-sm mt-1">当前阶段：{{ phaseLabelMap[uploadPhase] }}</p>
          <p class="text-sm mt-1">已完成分片：{{ completedChunks }} / {{ totalChunks }}</p>
          <p class="text-sm mt-1">正在上传：{{ activeChunks }}</p>
        </div>
      </div>

      <div
        v-if="isSmallFile && selectedFile"
        class="p-3 mb-3 rounded border border-[var(--color-warning)] bg-[rgba(210,153,34,0.08)] text-[var(--color-warning)] text-xs"
      >
        当前文件大小为 {{ selectedFileSizeText }}。分片上传仍可执行，但小文件通常更适合使用
        <router-link to="/upload/simple" class="underline">单文件上传</router-link>。
      </div>

      <div class="mb-4">
        <p class="section-label">存放路径 path</p>
        <input
          v-model="targetPath"
          class="oss-input"
          placeholder="demo/"
          :disabled="isUploading"
        />
      </div>

      <div class="flex gap-2 flex-wrap mb-4">
        <button
          class="btn btn-primary"
          :disabled="!selectedFile || isUploading"
          @click="startUpload"
        >
          <span v-if="isUploading" class="spinner" />
          {{ isUploading ? '上传中…' : uploadPhase === 'done' ? '重新上传' : '开始上传' }}
        </button>
        <button v-if="isUploading" class="btn btn-danger" @click="abortUpload">取消</button>
        <button
          v-if="selectedFile || uploadPhase !== 'idle'"
          class="btn btn-ghost"
          :disabled="isUploading"
          @click="clearSelectedFile"
        >
          清空
        </button>
      </div>

      <p v-if="uploadMessage" class="text-sm text-[var(--color-danger)] mb-3">{{ uploadMessage }}</p>

      <div v-if="uploadId" class="mb-3">
        <p class="section-label">uploadId</p>
        <code class="text-xs text-[var(--color-accent)] break-all">{{ uploadId }}</code>
      </div>

      <div v-if="chunkStates.length" class="mb-4">
        <div class="flex items-center justify-between text-xs text-[var(--color-muted)] mb-1">
          <span>{{ completedChunks }} / {{ totalChunks }} 分片已完成</span>
          <span>{{ overallProgress }}%</span>
        </div>
        <div class="progress-track mb-3">
          <div class="progress-fill" :style="{ width: `${overallProgress}%` }" />
        </div>

        <div class="flex flex-wrap gap-1 mb-2">
          <span
            v-for="chunk in chunkStates"
            :key="chunk.index"
            :title="buildChunkTooltip(chunk)"
            :class="[
              'inline-flex items-center justify-center w-7 h-7 rounded text-[10px] font-mono cursor-default',
              chunk.status === 'done'      ? 'bg-[var(--color-success)] text-[#0d1117]' :
              chunk.status === 'uploading' ? 'bg-[var(--color-accent)] text-[#0d1117] animate-pulse' :
              chunk.status === 'error'     ? 'bg-[var(--color-danger)] text-white' :
                                             'bg-[var(--color-border)] text-[var(--color-muted)]',
            ]"
          >
            {{ chunk.index }}
          </span>
        </div>

        <p class="text-[10px] text-[var(--color-muted)]">
          绿色表示已完成，蓝色表示上传中，红色表示失败，灰色表示待上传。
        </p>
      </div>

      <ResultPanel
        :status="mergeStatus"
        :result="mergeResult"
        :error="mergeError"
        label="合并结果 ObjectInfo"
      />
    </ApiCard>

    <ApiCard method="GET" path="/oss/multipart/parts" summary="查询已上传分片列表">
      <div class="grid grid-cols-1 md:grid-cols-2 gap-3 mb-4">
        <div>
          <p class="section-label">objectName</p>
          <input
            v-model="partsObjectName"
            class="oss-input"
            :placeholder="resolvedObjectName || 'demo/file.zip'"
          />
        </div>
        <div>
          <p class="section-label">uploadId</p>
          <input
            v-model="partsUploadId"
            class="oss-input"
            :placeholder="uploadId || '请输入 uploadId'"
          />
        </div>
      </div>

      <button
        class="btn btn-ghost"
        :disabled="partsStatus === 'loading'"
        @click="queryUploadedParts"
      >
        <span v-if="partsStatus === 'loading'" class="spinner" />
        查询已上传分片
      </button>

      <ResultPanel
        :status="partsStatus"
        :result="partsResult"
        :error="partsError"
        label="已上传分片列表"
      />
    </ApiCard>
  </div>
</template>
