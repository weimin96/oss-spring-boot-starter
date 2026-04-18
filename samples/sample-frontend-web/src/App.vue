<script setup lang="ts">
import {computed} from 'vue'
import {useRoute} from 'vue-router'
import {navGroups} from '@/router'

const route = useRoute()

const currentLabel = computed(() => {
  for (const g of navGroups) {
    const r = g.routes.find(r => r.name === route.name)
    if (r) return `${g.label} / ${r.label}`
  }
  return ''
})
</script>

<template>
  <div class="flex h-screen overflow-hidden">

    <!-- Sidebar -->
    <aside class="w-56 flex-shrink-0 flex flex-col border-r border-[var(--color-border)] overflow-y-auto">

      <!-- Logo -->
      <div class="px-4 py-5 border-b border-[var(--color-border)]">
        <div class="flex items-center gap-2">
          <span class="text-[var(--color-accent)] text-lg font-bold">◈</span>
          <div>
            <p class="text-sm font-bold leading-tight">OSS Demo</p>
            <p class="text-[10px] text-[var(--color-muted)]">oss-spring-boot-starter</p>
          </div>
        </div>
      </div>

      <!-- Nav -->
      <nav class="flex-1 px-2 py-3 space-y-4">
        <div v-for="group in navGroups" :key="group.label">
          <p class="px-2 mb-1 text-[10px] font-semibold uppercase tracking-widest text-[var(--color-muted)]">
            {{ group.icon }} {{ group.label }}
          </p>
          <router-link
              v-for="r in group.routes"
              :key="r.name"
              :to="r.path"
              class="flex items-center gap-2 px-3 py-1.5 rounded-md text-sm transition-all"
              :class="route.name === r.name
              ? 'bg-[rgba(88,166,255,0.1)] text-[var(--color-accent)]'
              : 'text-[var(--color-muted)] hover:text-[var(--color-text)] hover:bg-[var(--color-surface)]'"
          >
            {{ r.label }}
          </router-link>
        </div>
      </nav>

      <!-- Footer -->
      <div class="px-4 py-3 border-t border-[var(--color-border)]">
        <a
            href="https://github.com/weimin96/oss-spring-boot-starter"
            target="_blank"
            class="text-[10px] text-[var(--color-muted)] hover:text-[var(--color-accent)] transition-colors"
        >
          GitHub →
        </a>
      </div>
    </aside>

    <!-- Main -->
    <main class="flex-1 flex flex-col overflow-hidden">
      <!-- Top bar -->
      <header class="h-12 flex-shrink-0 flex items-center px-6 border-b border-[var(--color-border)] gap-3">
        <code class="text-xs text-[var(--color-muted)]">{{ currentLabel }}</code>
      </header>

      <!-- Content -->
      <div class="flex-1 overflow-y-auto px-6 py-5">
        <div class="max-w-3xl">
          <router-view v-slot="{ Component }">
            <transition name="fade" mode="out-in">
              <component :is="Component"/>
            </transition>
          </router-view>
        </div>
      </div>
    </main>
  </div>
</template>
