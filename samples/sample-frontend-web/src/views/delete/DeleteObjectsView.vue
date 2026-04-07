<script setup lang="ts">
import { ref } from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import ResultPanel from '@/components/ResultPanel.vue'
import { useResult } from '@/composables/useResult'
import { ossApi } from '@/api/oss'

// 批量删除
const batchInput = ref('demo/a.txt\ndemo/b.txt\ndemo/c.txt')
const { status: batchStatus, result: batchResult, error: batchError, execute: execBatch } = useResult()

function parseBatch(): string[] {
  return batchInput.value
    .split('\n')
    .map(s => s.trim())
    .filter(Boolean)
}
</script>

<template>
  <div>
    <h2 class="text-base font-semibold mb-1">批量删除文件</h2>
    <p class="text-sm text-[var(--color-muted)] mb-5">一次删除多个文件（最多 1000 个）</p>

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
  </div>
</template>