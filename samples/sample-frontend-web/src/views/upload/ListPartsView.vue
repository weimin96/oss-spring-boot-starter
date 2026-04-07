<script setup lang="ts">
import { ref } from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import ResultPanel from '@/components/ResultPanel.vue'
import { useResult } from '@/composables/useResult'
import { ossApi } from '@/api/oss'

// 查询已上传分片
const partsObjectName = ref('')
const partsUploadId = ref('')
const { status: partsStatus, result: partsResult, error: partsError, execute: execParts } = useResult()

// 从 URL 获取分片信息
const currentPath = ref('demo/')
function getObjectName(): string {
  return currentPath.value.replace(/\/$/, '',) + '/'
}
</script>

<template>
  <div>
    <h2 class="text-base font-semibold mb-1">查询已上传分片</h2>
    <p class="text-sm text-[var(--color-muted)] mb-5">查询指定 uploadId 已上传的分片列表，用于断点续传</p>

    <ApiCard method="GET" path="/oss/multipart/parts" summary="查询已上传的分片列表（断点续传参考）">
      <div class="grid grid-cols-2 gap-3 mb-4">
        <div>
          <p class="section-label">objectName</p>
          <input
            v-model="partsObjectName"
            class="oss-input"
            placeholder="demo/file.zip"
          />
        </div>
        <div>
          <p class="section-label">uploadId</p>
          <input
            v-model="partsUploadId"
            class="oss-input"
            placeholder="xxxx-xxxx"
          />
        </div>
      </div>
      <button
        class="btn btn-ghost"
        :disabled="partsStatus === 'loading'"
        @click="execParts(() => ossApi.multipart.listParts(
          partsObjectName,
          partsUploadId,
        ))"
      >
        <span v-if="partsStatus === 'loading'" class="spinner" />查询已上传分片
      </button>
      <ResultPanel
        :status="partsStatus"
        :result="partsResult"
        :error="partsError"
        label="已上传分片列表（可判断哪些分片已完成，用于断点续传）"
      />
    </ApiCard>
  </div>
</template>