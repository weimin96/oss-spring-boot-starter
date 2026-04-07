<script setup lang="ts">
import { ref } from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import { ossApi } from '@/api/oss'

// 分片下载（Range 请求）
const rangeKey = ref('demo/example.bin')
const rangeStart = ref(0)
const rangeEnd = ref(1048575) // 默认下载前 1 MB
const rangeStatus = ref<'idle' | 'loading' | 'success' | 'error'>('idle')
const rangeMsg = ref('')

async function downloadRange() {
  rangeStatus.value = 'loading'
  rangeMsg.value = ''
  try {
    const res = await ossApi.query.downloadRange(rangeKey.value, rangeStart.value, rangeEnd.value)
    const blob = res.data as Blob
    const contentRange = res.headers['content-range'] ?? ''
    // 以分片序号命名下载文件
    const fname = `${rangeKey.value.split('/').pop()}.part`
    triggerDownload(blob, fname)
    rangeStatus.value = 'success'
    rangeMsg.value = `下载成功：${blob.size} 字节${contentRange ? '  Content-Range: ' + contentRange : ''}`
  } catch (e: unknown) {
    rangeStatus.value = 'error'
    rangeMsg.value = e instanceof Error ? e.message : String(e)
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
</script>

<template>
  <div>
    <h2 class="text-base font-semibold mb-1">分片下载</h2>
    <p class="text-sm text-[var(--color-muted)] mb-5">使用 HTTP Range 请求下载文件的指定字节范围</p>

    <!-- 分片下载（Range 请求） -->
    <ApiCard method="GET" path="/oss/object/download/**" summary="分片下载（HTTP Range 请求）">
      <div class="text-xs text-[var(--color-muted)] mb-3 p-2 rounded bg-[var(--color-bg)] border border-[var(--color-border)]">
        发送 <code>Range: bytes=start-end</code> 请求头，适用于大文件断点续传、视频分段加载等场景。
        后端 previewObject 已支持 Range 分段响应。
      </div>
      <div class="grid grid-cols-3 gap-3 mb-4">
        <div>
          <p class="section-label">objectName</p>
          <input v-model="rangeKey" class="oss-input" placeholder="demo/example.bin" />
        </div>
        <div>
          <p class="section-label">start（字节，含）</p>
          <input v-model.number="rangeStart" type="number" class="oss-input" min="0" />
        </div>
        <div>
          <p class="section-label">end（字节，含）</p>
          <input v-model.number="rangeEnd" type="number" class="oss-input" min="0" />
        </div>
      </div>
      <p class="text-xs text-[var(--color-muted)] mb-3">
        当前 Range 头：<code class="text-[var(--color-accent)]">bytes={{ rangeStart }}-{{ rangeEnd }}</code>
        （{{ ((rangeEnd - rangeStart + 1) / 1024 / 1024).toFixed(2) }} MB）
      </p>
      <button
        class="btn btn-ghost"
        :disabled="rangeStatus === 'loading'"
        @click="downloadRange"
      >
        <span v-if="rangeStatus === 'loading'" class="spinner" />下载指定字节范围
      </button>
      <p
        v-if="rangeMsg"
        class="mt-3 text-sm"
        :class="rangeStatus === 'success' ? 'text-[var(--color-success)]' : 'text-[var(--color-danger)]'"
      >
        {{ rangeMsg }}
      </p>
    </ApiCard>
  </div>
</template>