<script setup lang="ts">
import { ref } from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import ResultPanel from '@/components/ResultPanel.vue'
import { useResult } from '@/composables/useResult'
import { ossApi } from '@/api/oss'

// Bucket 列表
const { status: bucketsStatus, result: bucketsResult, error: bucketsError, execute: execBuckets } = useResult()
</script>

<template>
  <div>
    <h2 class="text-base font-semibold mb-1">列举 Bucket</h2>
    <p class="text-sm text-[var(--color-muted)] mb-5">列举当前账号下所有可访问的 Bucket</p>

    <!-- 列举 Bucket -->
    <ApiCard method="GET" path="/oss/buckets" summary="列举所有 Bucket">
      <div class="text-xs text-[var(--color-muted)] mb-3 p-2 rounded bg-[var(--color-bg)] border border-[var(--color-border)]">
        ⚠️ 部分 S3 SDK 版本可能抛出 <code>HttpMessageConversionException</code>（Bucket 对象序列化问题）。
        后端 workaround：在 <code>application.yml</code> 中配置
        <code>spring.jackson.deserialization.fail-on-unknown-properties: false</code>
        或升级 SDK 版本。
      </div>
      <button class="btn btn-ghost" :disabled="bucketsStatus === 'loading'" @click="execBuckets(() => ossApi.query.listBuckets())">
        <span v-if="bucketsStatus === 'loading'" class="spinner" />列举 Buckets
      </button>
      <ResultPanel :status="bucketsStatus" :result="bucketsResult" :error="bucketsError" label="Bucket[]" />
    </ApiCard>
  </div>
</template>