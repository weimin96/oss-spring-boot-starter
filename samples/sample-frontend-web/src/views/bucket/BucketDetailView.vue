<script setup lang="ts">
import {ref} from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import ResultPanel from '@/components/ResultPanel.vue'
import {useResult} from '@/composables/useResult'
import {ossApi} from '@/api/oss'
import type {BucketDetailInfo} from '@/types'

// Bucket 详情
const bucketName = ref('my-bucket')
const {status, result, error, execute} = useResult<BucketDetailInfo>()
</script>

<template>
  <div>
    <h2 class="text-base font-semibold mb-1">Bucket 详情</h2>
    <p class="text-sm text-[var(--color-muted)] mb-5">查询指定 Bucket 的 ACL、总大小、对象总数、创建时间和标签</p>

    <ApiCard method="GET" path="/oss/buckets/{bucketName}" summary="查询指定 Bucket 详情">
      <div class="mb-3">
        <p class="section-label">bucketName</p>
        <input v-model="bucketName" class="oss-input" placeholder="my-bucket"/>
      </div>
      <button class="btn btn-ghost" :disabled="status === 'loading'"
              @click="execute(() => ossApi.bucket.getDetail(bucketName))">
        <span v-if="status === 'loading'" class="spinner"/>查询 Bucket 详情
      </button>
      <ResultPanel :status="status" :result="result" :error="error" label="BucketDetailInfo"/>
    </ApiCard>
  </div>
</template>
