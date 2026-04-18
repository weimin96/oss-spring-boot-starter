<script setup lang="ts">
import {computed} from 'vue'
import ObjectTreeBranch from '@/components/ObjectTreeBranch.vue'
import type {ObjectTreeEntry} from '@/utils/objectExplorer'

const props = withDefaults(defineProps<{
  nodes: ObjectTreeEntry[]
  title?: string
  emptyText?: string
  defaultExpandedDepth?: number
}>(), {
  title: '树形结果',
  emptyText: '没有数据',
  defaultExpandedDepth: 1,
})

function countKinds(nodes: ObjectTreeEntry[]): { folders: number; files: number } {
  return nodes.reduce((acc, node) => {
    if (node.kind === 'folder') {
      acc.folders += 1
    } else {
      acc.files += 1
    }

    const nested = countKinds(node.children)
    acc.folders += nested.folders
    acc.files += nested.files
    return acc
  }, {folders: 0, files: 0})
}

const stats = computed(() => countKinds(props.nodes))
</script>

<template>
  <div class="mt-4">
    <div class="flex flex-wrap items-center gap-2 mb-3">
      <p class="section-label mb-0">{{ title }}</p>
      <span class="badge badge-info">{{ nodes.length }} 个根节点</span>
      <span class="badge badge-warning">{{ stats.folders }} 个目录</span>
      <span class="badge badge-success">{{ stats.files }} 个文件</span>
    </div>

    <div v-if="nodes.length === 0" class="card text-sm text-[var(--color-muted)]">
      {{ emptyText }}
    </div>

    <div v-else class="card p-2 max-h-[420px] overflow-auto">
      <div class="space-y-1 text-xs">
        <ObjectTreeBranch
            v-for="node in nodes"
            :key="node.key"
            :node="node"
            :default-expanded-depth="defaultExpandedDepth"
        />
      </div>
    </div>
  </div>
</template>
