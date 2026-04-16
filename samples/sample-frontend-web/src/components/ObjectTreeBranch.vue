<script setup lang="ts">
import { computed, ref } from 'vue'
import { formatObjectExt, formatObjectSize, formatObjectTime } from '@/utils/objectExplorer'
import type { ObjectTreeEntry } from '@/utils/objectExplorer'

defineOptions({
  name: 'ObjectTreeBranch',
})

const props = withDefaults(defineProps<{
  node: ObjectTreeEntry
  level?: number
  defaultExpandedDepth?: number
}>(), {
  level: 0,
  defaultExpandedDepth: 1,
})

const hasChildren = computed(() => props.node.children.length > 0)
const paddingStyle = computed(() => ({
  paddingLeft: `${props.level * 16}px`,
}))
const expanded = ref(props.level < props.defaultExpandedDepth)
const toggleIcon = computed(() => expanded.value ? '▾' : '▸')

function syncExpanded(event: Event) {
  expanded.value = (event.currentTarget as HTMLDetailsElement).open
}
</script>

<template>
  <details
    v-if="hasChildren"
    :open="level < defaultExpandedDepth"
    class="group"
    @toggle="syncExpanded"
  >
    <summary class="list-none cursor-pointer">
      <div
        class="flex items-start gap-2 rounded px-2 py-2 hover:bg-[rgba(88,166,255,0.06)]"
        :style="paddingStyle"
      >
        <span class="mt-0.5 w-4 text-center text-[var(--color-muted)]">{{ toggleIcon }}</span>
        <div class="min-w-0 flex-1">
          <div class="flex flex-wrap items-center gap-2">
            <span class="font-medium break-all">{{ node.name }}</span>
            <span :class="node.kind === 'folder' ? 'badge badge-warning' : 'badge badge-info'">
              {{ node.kind === 'folder' ? '目录' : '文件' }}
            </span>
            <span class="text-[10px] text-[var(--color-muted)]">{{ formatObjectExt(node.ext, node.kind) }}</span>
          </div>
          <p class="mt-1 text-[11px] text-[var(--color-muted)] break-all">{{ node.uri }}</p>
          <div class="mt-1 flex flex-wrap gap-x-4 gap-y-1 text-[10px] text-[var(--color-muted)]">
            <span>大小：{{ formatObjectSize(node.size, node.kind) }}</span>
            <span>时间：{{ formatObjectTime(node.uploadTime) }}</span>
            <a
              v-if="node.url"
              :href="node.url"
              target="_blank"
              class="text-[var(--color-accent)] hover:underline"
            >
              打开链接
            </a>
          </div>
        </div>
      </div>
    </summary>
    <div class="space-y-1">
      <ObjectTreeBranch
        v-for="child in node.children"
        :key="child.key"
        :node="child"
        :level="level + 1"
        :default-expanded-depth="defaultExpandedDepth"
      />
    </div>
  </details>

  <div
    v-else
    class="flex items-start gap-2 rounded px-2 py-2"
    :style="paddingStyle"
  >
    <span class="mt-0.5 w-4 text-center text-[var(--color-muted)]">•</span>
    <div class="min-w-0 flex-1">
      <div class="flex flex-wrap items-center gap-2">
        <span class="font-medium break-all">{{ node.name }}</span>
        <span :class="node.kind === 'folder' ? 'badge badge-warning' : 'badge badge-info'">
          {{ node.kind === 'folder' ? '目录' : '文件' }}
        </span>
        <span class="text-[10px] text-[var(--color-muted)]">{{ formatObjectExt(node.ext, node.kind) }}</span>
      </div>
      <p class="mt-1 text-[11px] text-[var(--color-muted)] break-all">{{ node.uri }}</p>
      <div class="mt-1 flex flex-wrap gap-x-4 gap-y-1 text-[10px] text-[var(--color-muted)]">
        <span>大小：{{ formatObjectSize(node.size, node.kind) }}</span>
        <span>时间：{{ formatObjectTime(node.uploadTime) }}</span>
        <a
          v-if="node.url"
          :href="node.url"
          target="_blank"
          class="text-[var(--color-accent)] hover:underline"
        >
          打开链接
        </a>
      </div>
    </div>
  </div>
</template>
