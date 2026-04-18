<script setup lang="ts">
import {computed, ref} from 'vue'
import {useRoute} from 'vue-router'
import {navGroups} from '@/router'
import {
  applyRuntimeBackendBaseUrl,
  getConfiguredBackendBaseUrl,
  getEffectiveBackendBaseUrl,
  resetRuntimeBackendBaseUrl,
} from '@/api/http'
import {hasRuntimeBackendOverride, resolveEffectiveOssApiBaseUrl} from '@/api/endpoint'

const route = useRoute()
const backendBaseUrlInput = ref(getEffectiveBackendBaseUrl())
const backendConfigHint = ref('')
const backendConfigVersion = ref(0)

const currentLabel = computed(() => {
  for (const g of navGroups) {
    const r = g.routes.find(r => r.name === route.name)
    if (r) return `${g.label} / ${r.label}`
  }
  return ''
})

const currentBackendApiBaseUrl = computed(() => {
  backendConfigVersion.value
  return resolveEffectiveOssApiBaseUrl()
})

const runtimeOverrideEnabled = computed(() => {
  backendConfigVersion.value
  return hasRuntimeBackendOverride()
})

const defaultBackendBaseUrl = computed(() => getConfiguredBackendBaseUrl())

function refreshBackendConfigView(): void {
  backendBaseUrlInput.value = getEffectiveBackendBaseUrl()
  backendConfigVersion.value += 1
}

function applyBackendConfig(): void {
  const nextApiBaseUrl = applyRuntimeBackendBaseUrl(backendBaseUrlInput.value)
  refreshBackendConfigView()
  backendConfigHint.value = nextApiBaseUrl === '/api/oss'
      ? '已切换为同源 /api/oss，适合同域部署或本地开发代理。'
      : `已切换到 ${nextApiBaseUrl}`
}

function resetBackendConfig(): void {
  const nextApiBaseUrl = resetRuntimeBackendBaseUrl()
  refreshBackendConfigView()
  backendConfigHint.value = nextApiBaseUrl === '/api/oss'
      ? '已恢复为同源 /api/oss。'
      : `已恢复为仓库默认后端：${nextApiBaseUrl}`
}
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
      <div class="px-4 py-3 border-t border-[var(--color-border)] space-y-3">
        <a
            href="https://github.com/weimin96/oss-spring-boot-starter"
            target="_blank"
            class="text-[10px] text-[var(--color-muted)] hover:text-[var(--color-accent)] transition-colors"
        >
          GitHub →
        </a>
        <section class="rounded-lg border border-[var(--color-border)] bg-[var(--color-surface)] p-3">
          <p class="text-[10px] font-semibold uppercase tracking-widest text-[var(--color-muted)]">后端地址</p>
          <p class="mt-2 text-[11px] leading-5 text-[var(--color-muted)]">
            GitHub Pages 只发布静态前端。这里填写后端根地址，例如
            <code>https://demo.example.com</code>，页面会自动拼接 <code>/api/oss</code>。
          </p>
          <input
              v-model="backendBaseUrlInput"
              class="oss-input mt-3 text-[12px]"
              placeholder="https://demo.example.com"
          />
          <div class="mt-3 flex gap-2">
            <button class="btn btn-primary px-3 py-1.5 text-[11px]" @click="applyBackendConfig">应用</button>
            <button class="btn btn-ghost px-3 py-1.5 text-[11px]" @click="resetBackendConfig">恢复默认</button>
          </div>
          <p class="mt-3 break-all text-[10px] leading-5 text-[var(--color-muted)]">
            当前接口：<code>{{ currentBackendApiBaseUrl }}</code>
          </p>
          <p v-if="defaultBackendBaseUrl" class="mt-1 break-all text-[10px] leading-5 text-[var(--color-muted)]">
            仓库默认：<code>{{ defaultBackendBaseUrl }}</code>
          </p>
          <p v-if="runtimeOverrideEnabled" class="mt-1 text-[10px] leading-5 text-[var(--color-accent)]">
            当前使用浏览器本地保存的自定义后端地址。
          </p>
          <p v-if="backendConfigHint" class="mt-2 text-[10px] leading-5 text-[var(--color-accent)]">
            {{ backendConfigHint }}
          </p>
        </section>
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
