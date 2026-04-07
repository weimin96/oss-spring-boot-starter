<script setup lang="ts">
import { ref } from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import ResultPanel from '@/components/ResultPanel.vue'
import { useResult } from '@/composables/useResult'
import { ossApi } from '@/api/oss'

// 文件夹删除
const folderPath = ref('demo/old-folder/')
const { status: folderStatus, result: folderResult, error: folderError, execute: execFolder } = useResult()
</script>

<template>
  <div>
    <h2 class="text-base font-semibold mb-1">删除文件夹</h2>
    <p class="text-sm text-[var(--color-muted)] mb-5">递归删除文件夹下的所有文件</p>

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