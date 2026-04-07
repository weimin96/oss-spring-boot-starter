<script setup lang="ts">
import { ref } from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import ResultPanel from '@/components/ResultPanel.vue'
import { useResult } from '@/composables/useResult'
import { ossApi } from '@/api/oss'

// ── 单个删除 ──────────────────────────────────────────────────────────
const delKey = ref('demo/example.txt')
const { status: delStatus, result: delResult, error: delError, execute: execDel } = useResult()

// ── 批量删除 ──────────────────────────────────────────────────────────
const batchInput = ref('demo/a.txt\ndemo/b.txt\ndemo/c.txt')
const { status: batchStatus, result: batchResult, error: batchError, execute: execBatch } = useResult()

function parseBatch(): string[] {
  return batchInput.value
    .split('\n')
    .map(s => s.trim())
    .filter(Boolean)
}

// ── 文件夹删除 ────────────────────────────────────────────────────────
const folderPath = ref('demo/old-folder/')
const { status: folderStatus, result: folderResult, error: folderError, execute: execFolder } = useResult()
</script>

<template>
  <div>
    <h2 class="text-base font-semibold mb-1">文件删除</h2>
    <p class="text-sm text-[var(--color-muted)] mb-5">单个删除、批量删除、递归删除文件夹</p>

    <!-- 单个删除 -->
    <ApiCard method="DELETE" path="/oss/object" summary="删除单个文件">
      <div class="mb-4">
        <p class="section-label">objectName（文件全路径 key）<span class="text-[var(--color-danger)]">*</span></p>
        <input v-model="delKey" class="oss-input" placeholder="demo/example.txt" />
      </div>
      <button
        class="btn btn-danger"
        :disabled="delStatus === 'loading'"
        @click="execDel(() => ossApi.delete.deleteObject(delKey))"
      >
        <span v-if="delStatus === 'loading'" class="spinner" />删除文件
      </button>
      <ResultPanel :status="delStatus" :result="delResult" :error="delError" label="删除结果" />
    </ApiCard>

    <!-- 批量删除 -->
    <ApiCard method="DELETE" path="/oss/objects" summary="批量删除文件（一次最多 1000 个）">
      <div class="mb-4">
        <p class="section-label">objectNames（每行一个 key）<span class="text-[var(--color-danger)]">*</span></p>
        <textarea
          v-model="batchInput"
          class="oss-input"
          rows="5"
          placeholder="demo/a.txt&#10;demo/b.txt&#10;demo/c.txt"
          style="resize: vertical;"
        />
        <p class="text-xs text-[var(--color-muted)] mt-1">共 {{ parseBatch().length }} 个</p>
      </div>
      <button
        class="btn btn-danger"
        :disabled="batchStatus === 'loading' || parseBatch().length === 0"
        @click="execBatch(() => ossApi.delete.deleteObjects(parseBatch()))"
      >
        <span v-if="batchStatus === 'loading'" class="spinner" />批量删除
      </button>
      <ResultPanel :status="batchStatus" :result="batchResult" :error="batchError" label="批量删除结果" />
    </ApiCard>

    <!-- 文件夹删除 -->
    <ApiCard method="DELETE" path="/oss/folder" summary="删除文件夹（递归删除所有子对象）">
      <div class="p-3 mb-4 rounded border border-[var(--color-warning)] bg-[rgba(210,153,34,0.08)] text-[var(--color-warning)] text-xs">
        ⚠️ 此操作将递归删除文件夹下的所有文件，不可恢复，请谨慎操作。
      </div>
      <div class="mb-4">
        <p class="section-label">文件夹路径 path<span class="text-[var(--color-danger)]">*</span></p>
        <input v-model="folderPath" class="oss-input" placeholder="demo/old-folder/" />
      </div>
      <button
        class="btn btn-danger"
        :disabled="folderStatus === 'loading'"
        @click="execFolder(() => ossApi.delete.deleteFolder(folderPath))"
      >
        <span v-if="folderStatus === 'loading'" class="spinner" />递归删除文件夹
      </button>
      <ResultPanel :status="folderStatus" :result="folderResult" :error="folderError" label="删除结果" />
    </ApiCard>
  </div>
</template>
