<script setup lang="ts">
import type { Status } from '@/composables/useResult'

defineProps<{
  status: Status
  result?: unknown
  error?: string | null
  label?: string
}>()
</script>

<template>
  <div v-if="status !== 'idle'" class="mt-4">
    <!-- Loading -->
    <div v-if="status === 'loading'" class="flex items-center gap-2 text-[var(--color-muted)] text-sm">
      <span class="spinner" />
      <span>请求中…</span>
    </div>

    <!-- Error -->
    <div v-else-if="status === 'error'" class="card border-[var(--color-danger)]">
      <p class="section-label text-[var(--color-danger)]">错误</p>
      <p class="text-sm text-[var(--color-danger)]">{{ error }}</p>
    </div>

    <!-- Success -->
    <div v-else-if="status === 'success'">
      <p class="section-label">{{ label ?? '响应结果' }}</p>
      <pre class="code-block">{{ result === null || result === undefined ? '(void)' : JSON.stringify(result, null, 2) }}</pre>
    </div>
  </div>
</template>
