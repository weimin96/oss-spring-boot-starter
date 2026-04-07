<script setup lang="ts">
import { ref, reactive } from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import ResultPanel from '@/components/ResultPanel.vue'
import { useResult } from '@/composables/useResult'
import { ossApi } from '@/api/oss'

// ── 对象标签 ──────────────────────────────────────────────────────────
const objName = ref('demo/example.txt')

// 查询
const { status: getObjTagStatus, result: getObjTagResult, error: getObjTagError, execute: execGetObjTag } = useResult()

// 标签编辑器
const tagRows = reactive<{ key: string; value: string }[]>([
  { key: 'env', value: 'prod' },
  { key: 'owner', value: 'team-a' },
])
function addRow() { tagRows.push({ key: '', value: '' }) }
function removeRow(i: number) { tagRows.splice(i, 1) }
function buildTagMap(): Record<string, string> {
  return Object.fromEntries(tagRows.filter(r => r.key).map(r => [r.key, r.value]))
}

const { status: setObjTagStatus, result: setObjTagResult, error: setObjTagError, execute: execSetObjTag } = useResult()
const { status: mergeObjTagStatus, result: mergeObjTagResult, error: mergeObjTagError, execute: execMergeObjTag } = useResult()
const { status: delObjTagStatus, result: delObjTagResult, error: delObjTagError, execute: execDelObjTag } = useResult()

// ── Bucket 标签 ────────────────────────────────────────────────────────
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
    <h2 class="text-base font-semibold mb-1">标签管理</h2>
    <p class="text-sm text-[var(--color-muted)] mb-5">对象标签（CRUD）及 Bucket 标签管理</p>

    <!-- ── 对象标签区 ── -->
    <p class="section-label text-base mb-3 text-[var(--color-text)]">对象标签（Object Tags）</p>

    <!-- objectName 公共输入 -->
    <div class="card mb-4">
      <p class="section-label">objectName（以下对象标签操作共用）</p>
      <input v-model="objName" class="oss-input" placeholder="demo/example.txt" />
    </div>

    <!-- 查询 -->
    <ApiCard method="GET" path="/oss/object/tags" summary="获取对象标签">
      <button
        class="btn btn-ghost"
        :disabled="getObjTagStatus === 'loading'"
        @click="execGetObjTag(() => ossApi.objectTags.get(objName))"
      >
        <span v-if="getObjTagStatus === 'loading'" class="spinner" />查询标签
      </button>
      <ResultPanel :status="getObjTagStatus" :result="getObjTagResult" :error="getObjTagError" label="标签 Map" />
    </ApiCard>

    <!-- 标签编辑器（SET / MERGE 共用） -->
    <div class="card mb-4">
      <p class="section-label mb-3">标签编辑（SET 覆盖 / MERGE 合并 共用）</p>
      <div class="space-y-2 mb-3">
        <div v-for="(row, i) in tagRows" :key="i" class="flex gap-2 items-center">
          <input v-model="row.key" class="oss-input" placeholder="key" />
          <span class="text-[var(--color-muted)]">:</span>
          <input v-model="row.value" class="oss-input" placeholder="value" />
          <button class="btn btn-danger text-xs px-2 py-1" @click="removeRow(i)">✕</button>
        </div>
      </div>
      <button class="btn btn-ghost text-xs mb-4" @click="addRow">+ 添加标签</button>

      <div class="flex gap-2 flex-wrap">
        <!-- SET -->
        <div>
          <button
            class="btn btn-primary"
            :disabled="setObjTagStatus === 'loading'"
            @click="execSetObjTag(() => ossApi.objectTags.set(objName, buildTagMap()))"
          >
            <span v-if="setObjTagStatus === 'loading'" class="spinner" />PUT 覆盖设置
          </button>
          <ResultPanel :status="setObjTagStatus" :result="setObjTagResult" :error="setObjTagError" />
        </div>
        <!-- MERGE -->
        <div>
          <button
            class="btn btn-ghost"
            :disabled="mergeObjTagStatus === 'loading'"
            @click="execMergeObjTag(() => ossApi.objectTags.merge(objName, buildTagMap()))"
          >
            <span v-if="mergeObjTagStatus === 'loading'" class="spinner" />PATCH 合并更新
          </button>
          <ResultPanel :status="mergeObjTagStatus" :result="mergeObjTagResult" :error="mergeObjTagError" />
        </div>
      </div>
    </div>

    <!-- 删除标签 -->
    <ApiCard method="DELETE" path="/oss/object/tags" summary="删除对象的所有标签">
      <button
        class="btn btn-danger"
        :disabled="delObjTagStatus === 'loading'"
        @click="execDelObjTag(() => ossApi.objectTags.delete(objName))"
      >
        <span v-if="delObjTagStatus === 'loading'" class="spinner" />删除所有标签
      </button>
      <ResultPanel :status="delObjTagStatus" :result="delObjTagResult" :error="delObjTagError" />
    </ApiCard>

    <!-- ── Bucket 标签区 ── -->
    <p class="section-label text-base mb-3 mt-6 text-[var(--color-text)]">Bucket 标签（Bucket Tags）</p>

    <ApiCard method="GET" path="/oss/bucket/tags" summary="获取 Bucket 标签">
      <button
        class="btn btn-ghost"
        :disabled="getBucketTagStatus === 'loading'"
        @click="execGetBucketTag(() => ossApi.bucket.getTags())"
      >
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
      <button
        class="btn btn-primary"
        :disabled="setBucketTagStatus === 'loading'"
        @click="execSetBucketTag(() => ossApi.bucket.setTags(buildBucketTagMap()))"
      >
        <span v-if="setBucketTagStatus === 'loading'" class="spinner" />PUT 覆盖设置 Bucket 标签
      </button>
      <ResultPanel :status="setBucketTagStatus" :result="setBucketTagResult" :error="setBucketTagError" />
    </div>

    <ApiCard method="DELETE" path="/oss/bucket/tags" summary="删除 Bucket 所有标签">
      <button
        class="btn btn-danger"
        :disabled="delBucketTagStatus === 'loading'"
        @click="execDelBucketTag(() => ossApi.bucket.deleteTags())"
      >
        <span v-if="delBucketTagStatus === 'loading'" class="spinner" />删除 Bucket 所有标签
      </button>
      <ResultPanel :status="delBucketTagStatus" :result="delBucketTagResult" :error="delBucketTagError" />
    </ApiCard>
  </div>
</template>
