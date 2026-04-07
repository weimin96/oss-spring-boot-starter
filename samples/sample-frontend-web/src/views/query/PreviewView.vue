<script setup lang="ts">
import { ref } from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import { ossApi } from '@/api/oss'

// 预览
const previewKey = ref('demo/example.jpg')

async function previewFile() {
  const res = await ossApi.query.preview(previewKey.value)
  const url = URL.createObjectURL(res.data as Blob)
  window.open(url, '_blank')
}
</script>

<template>
  <div>
    <h2 class="text-base font-semibold mb-1">预览文件</h2>
    <p class="text-sm text-[var(--color-muted)] mb-5">在新标签页预览 OSS 中的文件（inline 模式）</p>

    <!-- 预览 -->
    <div class="card mb-4">
      <div class="flex items-center gap-3 mb-4">
        <span class="method method-GET">GET</span>
        <code class="text-xs text-[var(--color-muted)]">/oss/object/preview/**</code>
        <p class="text-sm font-medium">预览文件（inline）</p>
      </div>
      <div>
        <p class="section-label">预览 objectName</p>
        <input v-model="previewKey" class="oss-input mb-2" placeholder="demo/example.jpg" />
        <button class="btn btn-ghost" @click="previewFile">在新标签页预览</button>
      </div>
    </div>
  </div>
</template>