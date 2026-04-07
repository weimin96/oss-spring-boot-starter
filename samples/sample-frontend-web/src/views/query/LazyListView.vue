<script setup lang="ts">
import { ref } from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import ResultPanel from '@/components/ResultPanel.vue'
import { useResult } from '@/composables/useResult'
import { ossApi } from '@/api/oss'

// 懒加载
const lazyPath = ref('demo/')
const lazyMaxKeys = ref(10)
const lazyToken = ref('')
const { status: lazyStatus, result: lazyResult, error: lazyError, execute: execLazy } = useResult()
</script>

<template>
  <div>
    <h2 class="text-base font-semibold mb-1">懒加载分页</h2>
    <p class="text-sm text-[var(--color-muted)] mb-5">分页列举对象，支持断点翻页</p>

    <!-- 懒加载分页 -->
    <ApiCard method="GET" path="/oss/object/list/lazy" summary="懒加载列表（分页 / 断点翻页）">
      <div class="grid grid-cols-3 gap-3 mb-3">
        <div>
          <p class="section-label">path</p>
          <input v-model="lazyPath" class="oss-input" placeholder="demo/" />
        </div>
        <div>
          <p class="section-label">maxKeys</p>
          <input v-model.number="lazyMaxKeys" type="number" class="oss-input" min="1" />
        </div>
        <div>
          <p class="section-label">continuationToken（翻页用）</p>
          <input v-model="lazyToken" class="oss-input" placeholder="（首次留空）" />
        </div>
      </div>
      <button class="btn btn-ghost" :disabled="lazyStatus === 'loading'"
        @click="execLazy(() => ossApi.query.lazyList(lazyPath, lazyMaxKeys, lazyToken || undefined))">
        <span v-if="lazyStatus === 'loading'" class="spinner" />加载
      </button>
      <!-- 下一页 token 快捷填入 -->
      <p v-if="(lazyResult as any)?.nextContinuationToken" class="mt-2 text-xs text-[var(--color-muted)]">
        下一页 token：
        <code
          class="text-[var(--color-accent)] cursor-pointer underline"
          @click="lazyToken = (lazyResult as any).nextContinuationToken"
        >点击填入</code>
      </p>
      <ResultPanel :status="lazyStatus" :result="lazyResult" :error="lazyError" label="LazyListResult" />
    </ApiCard>
  </div>
</template>