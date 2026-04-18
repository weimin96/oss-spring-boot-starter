<script setup lang="ts">
import {ref} from 'vue'
import {ossApi} from '@/api/oss'

// 下载
const downloadKey = ref('demo/example.txt')

async function downloadFile() {
  const res = await ossApi.query.download(downloadKey.value)
  triggerDownload(res.data as Blob, downloadKey.value.split('/').pop() ?? 'file')
}

function triggerDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  setTimeout(() => URL.revokeObjectURL(url), 5000)
}
</script>

<template>
  <div>
    <h2 class="text-base font-semibold mb-1">下载文件</h2>
    <p class="text-sm text-[var(--color-muted)] mb-5">将 OSS 中的文件下载到本地（attachment 模式）</p>

    <!-- 完整下载 -->
    <div class="card mb-4">
      <div class="flex items-center gap-3 mb-4">
        <span class="method method-GET">GET</span>
        <code class="text-xs text-[var(--color-muted)]">/oss/object/download/**</code>
        <p class="text-sm font-medium">下载文件（attachment）</p>
      </div>
      <div>
        <p class="section-label">下载 objectName</p>
        <input v-model="downloadKey" class="oss-input mb-2" placeholder="demo/example.txt"/>
        <button class="btn btn-ghost" @click="downloadFile">触发浏览器下载</button>
      </div>
    </div>
  </div>
</template>