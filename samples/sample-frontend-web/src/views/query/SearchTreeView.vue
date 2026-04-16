<script setup lang="ts">
import { ref, computed } from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import ObjectTreePanel from '@/components/ObjectTreePanel.vue'
import ResultPanel from '@/components/ResultPanel.vue'
import { useResult } from '@/composables/useResult'
import { ossApi } from '@/api/oss'
import { normalizeTreeRoot } from '@/utils/objectExplorer'
import type { ObjectTreeNode } from '@/types'

// 关键字搜索树（带空结果处理）
const searchPath = ref('demo/')
const searchKeyword = ref('')
const { status: searchStatus, result: searchResult, error: searchError, execute: execSearch } = useResult<ObjectTreeNode | null>()
const searchNodes = computed(() => normalizeTreeRoot(searchResult.value))
</script>

<template>
  <div>
    <h2 class="text-base font-semibold mb-1">关键字搜索树</h2>
    <p class="text-sm text-[var(--color-muted)] mb-5">按关键字搜索目录树，筛选匹配的文件和文件夹</p>

    <!-- 关键字搜索树 -->
    <ApiCard method="GET" path="/oss/object/tree/search" summary="按关键字搜索目录树">
      <div class="text-xs text-[var(--color-muted)] mb-3 p-2 rounded bg-[var(--color-bg)] border border-[var(--color-border)]">
        ⚠️ 注意：若无匹配结果，后端可能返回包含不存在路径的空节点（非报错），前端已做判断处理。
      </div>
      <div class="grid grid-cols-2 gap-3 mb-3">
        <div>
          <p class="section-label">path</p>
          <input v-model="searchPath" class="oss-input" placeholder="demo/" />
        </div>
        <div>
          <p class="section-label">keyword</p>
          <input v-model="searchKeyword" class="oss-input" placeholder="输入搜索关键字" />
        </div>
      </div>
      <button class="btn btn-ghost" :disabled="searchStatus === 'loading'" @click="execSearch(() => ossApi.query.searchTree(searchPath, searchKeyword))">
        <span v-if="searchStatus === 'loading'" class="spinner" />搜索
      </button>
      <ResultPanel :status="searchStatus" :result="searchResult" :error="searchError">
        <template #success>
          <ObjectTreePanel
            title="过滤后的目录树"
            :nodes="searchNodes"
            empty-text="没有匹配结果"
            :default-expanded-depth="2"
          />
        </template>
      </ResultPanel>
    </ApiCard>
  </div>
</template>
