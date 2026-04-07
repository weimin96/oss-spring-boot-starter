<script setup lang="ts">
import { ref } from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import ResultPanel from '@/components/ResultPanel.vue'
import { useResult } from '@/composables/useResult'
import { ossApi } from '@/api/oss'

// 下一层级
const nextLevelPath = ref('demo/')
const { status: nextStatus, result: nextResult, error: nextError, execute: execNext } = useResult()
</script>

<template>
  <div>
    <h2 class="text-base font-semibold mb-1">列举下一层级</h2>
    <p class="text-sm text-[var(--color-muted)] mb-5">只列举指定路径的下一级文件和文件夹</p>

    <!-- 下一层级 -->
    <ApiCard method="GET" path="/oss/object/list/next-level" summary="列举下一层级的文件和文件夹">
      <div class="mb-3">
        <p class="section-label">path</p>
        <input v-model="nextLevelPath" class="oss-input" placeholder="demo/" />
      </div>
      <button class="btn btn-ghost" :disabled="nextStatus === 'loading'" @click="execNext(() => ossApi.query.listNextLevel(nextLevelPath))">
        <span v-if="nextStatus === 'loading'" class="spinner" />查询
      </button>
      <ResultPanel :status="nextStatus" :result="nextResult" :error="nextError" label="ObjectTreeNode[]" />
    </ApiCard>
  </div>
</template>