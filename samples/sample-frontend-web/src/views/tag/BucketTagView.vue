<script setup lang="ts">
import { ref, reactive } from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import ResultPanel from '@/components/ResultPanel.vue'
import { useResult } from '@/composables/useResult'
import { ossApi } from '@/api/oss'

// Bucket 标签
const bucketTagRows = reactive<{ key: string; value: string }[]>([
  { key: 'project', value: 'oss-demo' },
])
function addBucketRow() { bucketTagRows.push({ key: '', value: '' }) }
function removeBucketRow(i: number) { bucketTagRows.splice(i, 1) }
function buildBucketTagMap(): Record<string, string> {
  return Object.fromEntries(bucketTagRows.filter(r => r.key).map(r => [r.key, r.value]))
}

const { status: getBucketTagStatus, result: getBucketTagResult, error: getBucketTagError, execute: execGetBucketTag } = useResult()
const { status: setBucketTagStatus, result: setBucketTagResult, error: setBucketTagError, execute: execSetBucketTag } = useResult()
const { status: delBucketTagStatus, result: delBucketTagResult, error: delBucketTagError, execute: execDelBucketTag } = useResult()
</script>

<template>
  <div>
    <h2 class="text-base font-semibold mb-1">Bucket 标签管理</h2>
    <p class="text-sm text-[var(--color-muted)] mb-2">Bucket 的标签 CRUD 操作</p>

    <!-- Bucket 标签区 -->
    <ApiCard method="GET" path="/oss/bucket/tags" summary="获取 Bucket 标签">
      <button class="btn btn-ghost" :disabled="getBucketTagStatus === 'loading'"
        @click="execGetBucketTag(() => ossApi.bucket.getTags())">
        <span v-if="getBucketTagStatus === 'loading'" class="spinner" />查询 Bucket 标签
      </button>
      <ResultPanel :status="getBucketTagStatus" :result="getBucketTagResult" :error="getBucketTagError" label="Bucket 标签 Map" />
    </ApiCard>

    <div class="card mb-4">
      <p class="section-label mb-3">Bucket 标签编辑</p>
      <div class="space-y-2 mb-3">
        <div v-for="(row, i) in bucketTagRows" :key="i" class="flex gap-2 items-center">
          <input v-model="row.key" class="oss-input" placeholder="key" />
          <span class="text-[var(--color-muted)]">:</span>
          <input v-model="row.value" class="oss-input" placeholder="value" />
          <button class="btn btn-danger text-xs px-2 py-1" @click="removeBucketRow(i)">✕</button>
        </div>
      </div>
      <button class="btn btn-ghost text-xs mb-4" @click="addBucketRow">+ 添加标签</button>
      <button class="btn btn-primary" :disabled="setBucketTagStatus === 'loading'"
        @click="execSetBucketTag(() => ossApi.bucket.setTags(buildBucketTagMap()))">
        <span v-if="setBucketTagStatus === 'loading'" class="spinner" />PUT 覆盖设置 Bucket 标签
      </button>
      <ResultPanel :status="setBucketTagStatus" :result="setBucketTagResult" :error="setBucketTagError" label="PUT 结果（void）" />
    </div>

    <ApiCard method="DELETE" path="/oss/bucket/tags" summary="删除 Bucket 所有标签">
      <button class="btn btn-danger" :disabled="delBucketTagStatus === 'loading'"
        @click="execDelBucketTag(() => ossApi.bucket.deleteTags())">
        <span v-if="delBucketTagStatus === 'loading'" class="spinner" />删除 Bucket 所有标签
      </button>
      <ResultPanel :status="delBucketTagStatus" :result="delBucketTagResult" :error="delBucketTagError" label="DELETE 结果（void）" />
    </ApiCard>
  </div>
</template>