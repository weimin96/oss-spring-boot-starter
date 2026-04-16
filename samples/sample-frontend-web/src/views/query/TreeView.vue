<script setup lang="ts">
import { computed, ref } from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import ObjectTreePanel from '@/components/ObjectTreePanel.vue'
import ResultPanel from '@/components/ResultPanel.vue'
import { useResult } from '@/composables/useResult'
import { ossApi } from '@/api/oss'
import { normalizeTreeRoot } from '@/utils/objectExplorer'
import type { ObjectTreeNode } from '@/types'

// 目录树查询页只处理“请求成功但没有树”这类页面语义，
// 具体树结构渲染交给公共树形组件，避免每个页面重复写递归模板。
const treePath = ref('demo/')
const { status: treeStatus, result: treeResult, error: treeError, execute: execTree } = useResult<ObjectTreeNode | null>()
const treeNodes = computed(() => normalizeTreeRoot(treeResult.value))
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
      <ResultPanel :status="treeStatus" :result="treeResult" :error="treeError">
        <template #success>
          <ObjectTreePanel
            title="完整目录树"
            :nodes="treeNodes"
            empty-text="路径下无对象，或路径不存在"
            :default-expanded-depth="2"
          />
        </template>
      </ResultPanel>
    </ApiCard>
  </div>
</template>
