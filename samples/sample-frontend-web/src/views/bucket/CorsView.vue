<script setup lang="ts">
import { ref } from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import ResultPanel from '@/components/ResultPanel.vue'
import { useResult } from '@/composables/useResult'
import { ossApi } from '@/api/oss'

// CORS
const { status: getCorsStatus, result: getCorsResult, error: getCorsError, execute: execGetCors } = useResult()
const { status: allowCorsStatus, result: allowCorsResult, error: allowCorsError, execute: execAllowCors } = useResult()
const { status: delCorsStatus, result: delCorsResult, error: delCorsError, execute: execDelCors } = useResult()
</script>

<template>
  <div>
    <h2 class="text-base font-semibold mb-1">CORS 配置</h2>
    <p class="text-sm text-[var(--color-muted)] mb-5">配置跨域资源共享规则，允许浏览器跨域访问 OSS 资源</p>

    <!-- CORS -->
    <div class="card mb-4">
      <p class="text-sm font-medium mb-4">跨域资源共享（CORS）</p>
      <div class="flex gap-2 mb-2 flex-wrap">
        <button class="btn btn-ghost" :disabled="getCorsStatus === 'loading'"
          @click="execGetCors(() => ossApi.bucket.getCors())">
          <span v-if="getCorsStatus === 'loading'" class="spinner" />GET 查询 CORS
        </button>
        <button class="btn btn-primary" :disabled="allowCorsStatus === 'loading'"
          @click="execAllowCors(() => ossApi.bucket.allowAllCors())">
          <span v-if="allowCorsStatus === 'loading'" class="spinner" />PUT 允许所有来源
        </button>
        <button class="btn btn-danger" :disabled="delCorsStatus === 'loading'"
          @click="execDelCors(() => ossApi.bucket.deleteCors())">
          <span v-if="delCorsStatus === 'loading'" class="spinner" />DELETE 删除 CORS
        </button>
      </div>
      <ResultPanel :status="getCorsStatus" :result="getCorsResult" :error="getCorsError" label="CORS 规则列表" />
      <ResultPanel :status="allowCorsStatus" :result="allowCorsResult" :error="allowCorsError" label="设置结果（void）" />
      <ResultPanel :status="delCorsStatus" :result="delCorsResult" :error="delCorsError" label="删除结果（void）" />
    </div>
  </div>
</template>