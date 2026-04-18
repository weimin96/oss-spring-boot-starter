<script setup lang="ts">
import {ref} from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import ResultPanel from '@/components/ResultPanel.vue'
import {useResult} from '@/composables/useResult'
import {ossApi} from '@/api/oss'

// 下载预签名 URL
const getObject = ref('demo/example.jpg')
const getExpiry = ref(3600)
const {status: getStatus, result: getResult, error: getError, execute: execGet} = useResult()

function copyUrl(url: unknown) {
  navigator.clipboard.writeText(String(url))
}
</script>

<template>
  <div>
    <h2 class="text-base font-semibold mb-1">下载预签名 URL</h2>
    <p class="text-sm text-[var(--color-muted)] mb-5">生成临时文件下载链接，无需鉴权即可访问</p>

    <!-- 下载预签名 -->
    <ApiCard method="GET" path="/oss/presign/get" summary="生成文件下载预签名 URL">
      <div class="grid grid-cols-2 gap-3 mb-4">
        <div>
          <p class="section-label">objectName <span class="text-[var(--color-danger)]">*</span></p>
          <input v-model="getObject" class="oss-input" placeholder="demo/example.jpg"/>
        </div>
        <div>
          <p class="section-label">有效期（秒，默认 3600）</p>
          <input v-model.number="getExpiry" type="number" class="oss-input" min="1"/>
        </div>
      </div>
      <button
          class="btn btn-ghost"
          :disabled="getStatus === 'loading'"
          @click="execGet(() => ossApi.presign.getUrl(getObject, getExpiry))"
      >
        <span v-if="getStatus === 'loading'" class="spinner"/>生成链接
      </button>

      <div v-if="getStatus === 'success' && getResult" class="mt-4">
        <p class="section-label">预签名 URL（GET）</p>
        <div class="code-block break-all">{{ getResult }}</div>
        <div class="flex gap-2 mt-2">
          <button class="btn btn-ghost text-xs" @click="copyUrl(getResult)">复制链接</button>
          <a :href="String(getResult)" target="_blank" class="btn btn-success text-xs">在浏览器打开</a>
        </div>
      </div>
      <ResultPanel v-if="getStatus === 'error'" :status="getStatus" :result="null" :error="getError"/>
    </ApiCard>
  </div>
</template>