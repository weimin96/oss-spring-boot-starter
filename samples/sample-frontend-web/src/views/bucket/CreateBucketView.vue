<script setup lang="ts">
import { ref } from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import ResultPanel from '@/components/ResultPanel.vue'
import { useResult } from '@/composables/useResult'
import { ossApi } from '@/api/oss'

// 创建 Bucket
const newBucketName = ref('my-new-bucket')
const { status: createStatus, result: createResult, error: createError, execute: execCreate } = useResult()
</script>

<template>
  <div>
    <h2 class="text-base font-semibold mb-1">创建 Bucket</h2>
    <p class="text-sm text-[var(--color-muted)] mb-5">创建一个新的 OSS Bucket</p>

    <!-- 创建 Bucket -->
    <ApiCard method="POST" path="/oss/bucket" summary="创建 Bucket（返回 void）">
      <div class="mb-3">
        <p class="section-label">bucketName</p>
        <input v-model="newBucketName" class="oss-input" placeholder="my-new-bucket" />
      </div>
      <button class="btn btn-primary" :disabled="createStatus === 'loading'"
        @click="execCreate(() => ossApi.bucket.create(newBucketName))">
        <span v-if="createStatus === 'loading'" class="spinner" />创建 Bucket
      </button>
      <ResultPanel :status="createStatus" :result="createResult" :error="createError" label="创建结果（void）" />
    </ApiCard>
  </div>
</template>