<script setup lang="ts">
import {ref} from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import {ossApi} from '@/api/oss'

const folderPath = ref('demo/')
const zipFilename = ref('')
const downloading = ref(false)
const errorMessage = ref('')

async function downloadFolderZip() {
  if (!folderPath.value || downloading.value) return

  downloading.value = true
  errorMessage.value = ''
  try {
    const response = await ossApi.query.downloadFolder(folderPath.value, zipFilename.value || undefined)
    const blob = response.data as Blob
    const filename = resolveFilename(response.headers['content-disposition'], zipFilename.value, folderPath.value)
    triggerDownload(blob, filename)
  } catch (error: unknown) {
    errorMessage.value = error instanceof Error ? error.message : String(error)
  } finally {
    downloading.value = false
  }
}

function resolveFilename(contentDisposition: string | undefined, customFilename: string, path: string) {
  const fromHeader = parseFilenameFromDisposition(contentDisposition)
  if (fromHeader) return fromHeader
  if (customFilename.trim()) return ensureZipSuffix(customFilename.trim())

  const normalizedPath = path.replace(/\\/g, '/').replace(/\/+$/, '')
  const folderName = normalizedPath.split('/').pop() || 'folder-download'
  return ensureZipSuffix(folderName)
}

function parseFilenameFromDisposition(contentDisposition?: string) {
  if (!contentDisposition) return ''
  const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i)
  if (utf8Match?.[1]) {
    return decodeURIComponent(utf8Match[1])
  }
  const plainMatch = contentDisposition.match(/filename="([^"]+)"/i)
  return plainMatch?.[1] ?? ''
}

function ensureZipSuffix(filename: string) {
  return filename.toLowerCase().endsWith('.zip') ? filename : `${filename}.zip`
}

function triggerDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()
  setTimeout(() => URL.revokeObjectURL(url), 5000)
}
</script>

<template>
  <div>
    <h2 class="text-base font-semibold mb-1">文件夹压缩下载</h2>
    <p class="text-sm text-[var(--color-muted)] mb-5">按路径前缀列举对象，并流式压缩为 ZIP 下载到本地</p>

    <ApiCard method="GET" path="/oss/folder/download" summary="按路径前缀压缩下载 ZIP">
      <div class="mb-3">
        <p class="section-label">path</p>
        <input v-model="folderPath" class="oss-input" placeholder="demo/"/>
      </div>

      <div class="mb-4">
        <p class="section-label">filename（可选）</p>
        <input v-model="zipFilename" class="oss-input" placeholder="demo-archive.zip"/>
      </div>

      <p class="text-xs text-[var(--color-muted)] mb-4">
        目录占位对象不会写入 ZIP，只有 prefix 下的真实对象会按相对路径进入压缩包。
      </p>

      <p v-if="errorMessage" class="text-sm text-[var(--color-danger)] mb-3">{{ errorMessage }}</p>

      <button class="btn btn-ghost" :disabled="!folderPath || downloading" @click="downloadFolderZip">
        <span v-if="downloading" class="spinner"/>
        {{ downloading ? '压缩下载中…' : '下载 ZIP' }}
      </button>
    </ApiCard>
  </div>
</template>
