<script setup lang="ts">
import { computed } from 'vue'
import { formatObjectExt, formatObjectSize, formatObjectTime } from '@/utils/objectExplorer'
import type { ObjectListItem } from '@/utils/objectExplorer'

const props = withDefaults(defineProps<{
  items: ObjectListItem[]
  title?: string
  emptyText?: string
}>(), {
  title: '列表结果',
  emptyText: '没有数据',
})

const folderCount = computed(() => props.items.filter(item => item.kind === 'folder').length)
const fileCount = computed(() => props.items.length - folderCount.value)
</script>

<template>
  <div class="mt-4">
    <div class="flex flex-wrap items-center gap-2 mb-3">
      <p class="section-label mb-0">{{ title }}</p>
      <span class="badge badge-info">{{ items.length }} 条</span>
      <span class="badge badge-warning">{{ folderCount }} 个目录</span>
      <span class="badge badge-success">{{ fileCount }} 个文件</span>
    </div>

    <div v-if="items.length === 0" class="card text-sm text-[var(--color-muted)]">
      {{ emptyText }}
    </div>

    <div v-else class="card p-0 overflow-hidden">
      <div class="overflow-x-auto">
        <table class="w-full text-xs">
          <thead class="bg-[var(--color-bg)] text-[var(--color-muted)]">
            <tr>
              <th class="px-3 py-2 text-left font-semibold">名称</th>
              <th class="px-3 py-2 text-left font-semibold">类型</th>
              <th class="px-3 py-2 text-left font-semibold">后缀</th>
              <th class="px-3 py-2 text-left font-semibold">大小</th>
              <th class="px-3 py-2 text-left font-semibold">上传时间</th>
              <th class="px-3 py-2 text-left font-semibold">访问地址</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="item in items"
              :key="item.key"
              class="border-t border-[var(--color-border)] align-top"
            >
              <td class="px-3 py-3 min-w-[220px]">
                <p class="font-medium text-[var(--color-text)] break-all">{{ item.name }}</p>
                <p class="mt-1 text-[var(--color-muted)] break-all">{{ item.uri }}</p>
              </td>
              <td class="px-3 py-3 whitespace-nowrap">
                <span :class="item.kind === 'folder' ? 'badge badge-warning' : 'badge badge-info'">
                  {{ item.kind === 'folder' ? '目录' : '文件' }}
                </span>
              </td>
              <td class="px-3 py-3 whitespace-nowrap">{{ formatObjectExt(item.ext, item.kind) }}</td>
              <td class="px-3 py-3 whitespace-nowrap">{{ formatObjectSize(item.size, item.kind) }}</td>
              <td class="px-3 py-3 whitespace-nowrap">{{ formatObjectTime(item.uploadTime) }}</td>
              <td class="px-3 py-3 min-w-[160px]">
                <a
                  v-if="item.url"
                  :href="item.url"
                  target="_blank"
                  class="text-[var(--color-accent)] hover:underline break-all"
                >
                  打开链接
                </a>
                <span v-else class="text-[var(--color-muted)]">-</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>
