<script setup lang="ts">
import ApiCard from '@/components/ApiCard.vue'
import ResultPanel from '@/components/ResultPanel.vue'
import { useResult } from '@/composables/useResult'
import { ossApi } from '@/api/oss'
import type { BucketInfo } from '@/types'

// Bucket 列表
const { status: bucketsStatus, result: bucketsResult, error: bucketsError, execute: execBuckets } = useResult<BucketInfo[]>()
</script>

<template>
  <div>
    <h2 class="text-base font-semibold mb-1">列举 Bucket</h2>
    <p class="text-sm text-[var(--color-muted)] mb-5">列举当前账号下所有可访问的 Bucket</p>

    <!-- 列举 Bucket -->
    <ApiCard method="GET" path="/oss/buckets" summary="列举所有 Bucket">
      <button class="btn btn-ghost" :disabled="bucketsStatus === 'loading'" @click="execBuckets(() => ossApi.query.listBuckets())">
        <span v-if="bucketsStatus === 'loading'" class="spinner" />列举 Buckets
      </button>
      <ResultPanel :status="bucketsStatus" :result="bucketsResult" :error="bucketsError" label="BucketInfo[]" />
    </ApiCard>
  </div>
</template>
