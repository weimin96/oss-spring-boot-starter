<script setup lang="ts">
import { useSlots } from 'vue'
import type { Status } from '@/composables/useResult'

defineProps<{
  status: Status
  result?: unknown
  error?: string | null
  label?: string
}>()

const slots = useSlots()

function displayResult(result: unknown): string {
  if (result === null || result === undefined) return '✓ 操作成功'
  if (typeof result === 'boolean') return String(result)
  if (typeof result === 'string') return result
  return JSON.stringify(result, null, 2)
}
</script>

<template>
  <div v-if="status !== 'idle'" class="mt-4">
    <!-- Loading -->
    <div v-if="status === 'loading'" class="flex items-center gap-2 text-[var(--color-muted)] text-sm">
      <span class="spinner" />
      <span>请求中…</span>
    </div>

    <!-- Error -->
    <div v-else-if="status === 'error'" class="card" style="border-color: var(--color-danger)">
      <p class="section-label" style="color: var(--color-danger)">错误</p>
      <p class="text-sm" style="color: var(--color-danger)">{{ error }}</p>
    </div>

    <!-- Success -->
    <div v-else-if="status === 'success'">
      <slot v-if="slots.success" name="success" :result="result" />
      <template v-else>
        <p class="section-label">{{ label ?? '响应结果' }}</p>
        <pre class="code-block">{{ displayResult(result) }}</pre>
      </template>
    </div>
  </div>
</template>
