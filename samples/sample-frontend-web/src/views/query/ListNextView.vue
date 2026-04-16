<script setup lang="ts">
import { computed, ref } from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import ObjectListPanel from '@/components/ObjectListPanel.vue'
import ResultPanel from '@/components/ResultPanel.vue'
import { useResult } from '@/composables/useResult'
import { ossApi } from '@/api/oss'
import { normalizeObjectListItems } from '@/utils/objectExplorer'
import type { ObjectTreeNode } from '@/types'

// 下一层级
const nextLevelPath = ref('demo/')
const { status: nextStatus, result: nextResult, error: nextError, execute: execNext } = useResult<ObjectTreeNode[]>()
const nextItems = computed(() => normalizeObjectListItems(nextResult.value ?? []))
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
      <ResultPanel :status="nextStatus" :result="nextResult" :error="nextError">
        <template #success>
          <ObjectListPanel
            title="下一层级列表"
            :items="nextItems"
            empty-text="当前路径下一层没有文件或文件夹"
          />
        </template>
      </ResultPanel>
    </ApiCard>
  </div>
</template>
