<script setup lang="ts">
defineProps<{
  method: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH'
  path: string
  summary: string
}>()
</script>

<template>
  <div class="card mb-4">
    <!-- Header -->
    <div class="flex items-start gap-3 mb-4">
      <span :class="`method method-${method}`">{{ method }}</span>
      <div class="flex-1 min-w-0">
        <code class="text-xs text-[var(--color-muted)]">{{ path }}</code>
        <p class="text-sm font-medium mt-0.5">{{ summary }}</p>
      </div>
    </div>
    <!-- Slot: form + result -->
    <slot />
  </div>
</template>
