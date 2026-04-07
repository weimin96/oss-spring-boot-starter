<script setup lang="ts">
import { ref } from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import ResultPanel from '@/components/ResultPanel.vue'
import { useResult } from '@/composables/useResult'
import { ossApi } from '@/api/oss'

// 文件夹树
const folderTreePath = ref('demo/')
const { status: folderTreeStatus, result: folderTreeResult, error: folderTreeError, execute: execFolderTree } = useResult()
</script>

<template>
  <div>
    <h2 class="text-base font-semibold mb-1">文件夹树</h2>
    <p class="text-sm text-[var(--color-muted)] mb-5">获取指定路径下的文件夹树（不含文件）</p>

    <!-- 文件夹树 -->
    <ApiCard method="GET" path="/oss/object/tree/folder" summary="获取文件夹树（不含文件）">
      <div class="mb-3">
        <p class="section-label">path</p>
        <input v-model="folderTreePath" class="oss-input" placeholder="demo/" />
      </div>
      <button class="btn btn-ghost" :disabled="folderTreeStatus === 'loading'" @click="execFolderTree(() => ossApi.query.getFolderTree(folderTreePath))">
        <span v-if="folderTreeStatus === 'loading'" class="spinner" />获取
      </button>
      <ResultPanel :status="folderTreeStatus" :result="folderTreeResult" :error="folderTreeError" label="ObjectTreeNode[]（仅文件夹）" />
    </ApiCard>
  </div>
</template>