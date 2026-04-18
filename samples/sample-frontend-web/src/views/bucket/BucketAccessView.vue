<script setup lang="ts">
import {computed, ref} from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import ResultPanel from '@/components/ResultPanel.vue'
import {useResult} from '@/composables/useResult'
import {ossApi} from '@/api/oss'
import type {BucketAccessInfo, BucketCannedAcl} from '@/types'

// Bucket ACL
const bucketName = ref('my-bucket')
const accessAcl = ref<BucketCannedAcl>('private')
const {status: getStatus, result: getResult, error: getError, execute: execGet} = useResult<BucketAccessInfo>()
const {status: setStatus, result: setResult, error: setError, execute: execSet} = useResult<BucketAccessInfo>()
const aclUnsupported = computed(() => getResult.value?.supported === false)
</script>

<template>
  <div>
    <h2 class="text-base font-semibold mb-1">Bucket ACL（建议通过访问策略控制而不是ACL）</h2>
    <p class="text-sm text-[var(--color-muted)] mb-5">查询或设置指定 Bucket 的 S3 默认 ACL 权限</p>

    <ApiCard method="PUT" path="/oss/buckets/{bucketName}/access" summary="查询或设置指定 Bucket ACL">
      <div class="grid grid-cols-2 gap-3 mb-4">
        <div>
          <p class="section-label">bucketName</p>
          <input v-model="bucketName" class="oss-input" placeholder="my-bucket"/>
        </div>
        <div>
          <p class="section-label">acl</p>
          <select v-model="accessAcl" class="oss-input">
            <option value="private">private</option>
            <option value="public-read">public-read</option>
            <option value="public-read-write">public-read-write</option>
            <option value="authenticated-read">authenticated-read</option>
          </select>
        </div>
      </div>

      <div
          class="p-3 mb-4 rounded border border-[var(--color-border)] bg-[var(--color-bg)] text-xs text-[var(--color-muted)]">
        部分 S3 兼容存储只兼容对象接口，不支持 Bucket ACL。后端如果识别到底层返回 <code>NotImplemented</code>，
        会返回明确的“不支持”错误，而不是继续伪装成一般设置失败。
      </div>

      <div class="flex gap-2 flex-wrap">
        <button class="btn btn-ghost" :disabled="getStatus === 'loading'"
                @click="execGet(() => ossApi.bucket.getAccess(bucketName))">
          <span v-if="getStatus === 'loading'" class="spinner"/>查询 ACL
        </button>
        <button class="btn btn-primary" :disabled="setStatus === 'loading' || aclUnsupported"
                @click="execSet(() => ossApi.bucket.setAccess(bucketName, accessAcl))">
          <span v-if="setStatus === 'loading'" class="spinner"/>设置 ACL
        </button>
      </div>

      <div v-if="aclUnsupported && getResult?.message" class="mt-3 text-sm text-[var(--color-danger)]">
        {{ getResult.message }}
      </div>

      <ResultPanel :status="getStatus" :result="getResult" :error="getError" label="BucketAccessInfo"/>
      <ResultPanel :status="setStatus" :result="setResult" :error="setError" label="BucketAccessInfo"/>
    </ApiCard>
  </div>
</template>
