<script setup lang="ts">
import { ref, reactive } from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import ResultPanel from '@/components/ResultPanel.vue'
import { useResult } from '@/composables/useResult'
import { ossApi } from '@/api/oss'

// 对象标签
const objName = ref('demo/example.txt')

const { status: getObjTagStatus, result: getObjTagResult, error: getObjTagError, execute: execGetObjTag } = useResult()

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
</script>

<template>
  <div>
    <h2 class="text-base font-semibold mb-1">对象标签管理</h2>
    <p class="text-sm text-[var(--color-muted)] mb-2">对象的标签 CRUD 操作</p>
    <p class="text-xs text-[var(--color-muted)] mb-5">
      ℹ️ PUT / PATCH / DELETE 标签接口后端返回 void（data 为 null），ResultPanel 已做处理显示「操作成功」。
    </p>

    <!-- 对象标签区 -->
    <div class="card mb-4">
      <p class="section-label">objectName（以下对象标签操作共用）</p>
      <input v-model="objName" class="oss-input" placeholder="demo/example.txt" />
    </div>

    <!-- GET 标签 -->
    <ApiCard method="GET" path="/oss/object/tags" summary="获取对象标签">
      <button class="btn btn-ghost" :disabled="getObjTagStatus === 'loading'"
        @click="execGetObjTag(() => ossApi.objectTags.get(objName))">
        <span v-if="getObjTagStatus === 'loading'" class="spinner" />查询标签
      </button>
      <ResultPanel :status="getObjTagStatus" :result="getObjTagResult" :error="getObjTagError" label="标签 Map" />
    </ApiCard>

    <!-- 标签编辑器（PUT / PATCH 共用） -->
    <div class="card mb-4">
      <p class="section-label mb-3">标签编辑（PUT 覆盖 / PATCH 合并 共用）</p>
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
        <div>
          <button class="btn btn-primary" :disabled="setObjTagStatus === 'loading'"
            @click="execSetObjTag(() => ossApi.objectTags.set(objName, buildTagMap()))">
            <span v-if="setObjTagStatus === 'loading'" class="spinner" />PUT 覆盖设置
          </button>
          <ResultPanel :status="setObjTagStatus" :result="setObjTagResult" :error="setObjTagError" label="PUT 结果（void）" />
        </div>
        <div>
          <button class="btn btn-ghost" :disabled="mergeObjTagStatus === 'loading'"
            @click="execMergeObjTag(() => ossApi.objectTags.merge(objName, buildTagMap()))">
            <span v-if="mergeObjTagStatus === 'loading'" class="spinner" />PATCH 合并更新
          </button>
          <ResultPanel :status="mergeObjTagStatus" :result="mergeObjTagResult" :error="mergeObjTagError" label="PATCH 结果（void）" />
        </div>
      </div>
    </div>

    <!-- DELETE 标签 -->
    <ApiCard method="DELETE" path="/oss/object/tags" summary="删除对象的所有标签">
      <button class="btn btn-danger" :disabled="delObjTagStatus === 'loading'"
        @click="execDelObjTag(() => ossApi.objectTags.delete(objName))">
        <span v-if="delObjTagStatus === 'loading'" class="spinner" />删除所有标签
      </button>
      <ResultPanel :status="delObjTagStatus" :result="delObjTagResult" :error="delObjTagError" label="DELETE 结果（void）" />
    </ApiCard>
  </div>
</template>