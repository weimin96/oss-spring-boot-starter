<script setup lang="ts">
import { ref, computed } from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import ResultPanel from '@/components/ResultPanel.vue'
import { useResult } from '@/composables/useResult'
import { ossApi } from '@/api/oss'
import type { ObjectTreeNode } from '@/types'

// 目录树（带空结果处理）
const treePath = ref('demo/')
const { status: treeStatus, result: treeResult, error: treeError, execute: execTree } = useResult<ObjectTreeNode | null>()
// 判断是否为空节点（路径不存在时后端返回无 children 的占位节点）
const treeIsEmpty = computed(() => {
  if (treeStatus.value !== 'success') return false
  const node = treeResult.value as ObjectTreeNode | null
  return !node
})
</script>

<template>
  <div>
    <h2 class="text-base font-semibold mb-1">完整目录树</h2>
    <p class="text-sm text-[var(--color-muted)] mb-5">获取指定路径下的完整目录树结构</p>

    <!-- 完整目录树 -->
    <ApiCard method="GET" path="/oss/object/tree" summary="获取完整目录树">
      <div class="mb-3">
        <p class="section-label">path</p>
        <input v-model="treePath" class="oss-input" placeholder="demo/" />
      </div>
      <button class="btn btn-ghost" :disabled="treeStatus === 'loading'" @click="execTree(() => ossApi.query.getTree(treePath))">
        <span v-if="treeStatus === 'loading'" class="spinner" />获取目录树
      </button>
      <!-- 空结果提示：路径不存在时后端返回占位节点 -->
      <div v-if="treeIsEmpty" class="mt-3 p-2 rounded border border-[var(--color-warning)] bg-[rgba(210,153,34,0.08)] text-[var(--color-warning)] text-xs">
        ⚠️ 路径下无对象，或路径不存在（后端返回空节点，非报错）
      </div>
      <ResultPanel v-else :status="treeStatus" :result="treeResult" :error="treeError" label="ObjectTreeNode（树形结构）" />
    </ApiCard>
  </div>
</template>