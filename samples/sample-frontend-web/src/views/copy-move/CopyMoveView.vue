<script setup lang="ts">
import { ref } from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import ResultPanel from '@/components/ResultPanel.vue'
import { useResult } from '@/composables/useResult'
import { ossApi } from '@/api/oss'

// 复制
const copySource = ref('demo/source.txt')
const copyDest = ref('demo/backup/source.txt')
const { status: copyStatus, result: copyResult, error: copyError, execute: execCopy } = useResult()

// 移动
const moveSource = ref('demo/source.txt')
const moveDest = ref('demo/archive/')
const { status: moveStatus, result: moveResult, error: moveError, execute: execMove } = useResult()
</script>

<template>
  <div>
    <h2 class="text-base font-semibold mb-1">复制 / 移动</h2>
    <p class="text-sm text-[var(--color-muted)] mb-2">同 Bucket 内文件复制与移动（移动 = 复制 + 删除源）</p>
    <p class="text-xs text-[var(--color-muted)] mb-5">
      ℹ️ 这两个接口后端返回 <code>R.success("xxx")</code>，<code>data</code> 字段为 null（void 语义），属正常响应。
    </p>

    <!-- 复制 -->
    <ApiCard method="POST" path="/oss/object/copy" summary="复制文件（同 Bucket 内）">
      <div class="grid grid-cols-2 gap-3 mb-4">
        <div>
          <p class="section-label">sourceKey（源文件 key）<span class="text-[var(--color-danger)]">*</span></p>
          <input v-model="copySource" class="oss-input" placeholder="demo/source.txt" />
        </div>
        <div>
          <p class="section-label">destKey（目标文件 key）<span class="text-[var(--color-danger)]">*</span></p>
          <input v-model="copyDest" class="oss-input" placeholder="demo/backup/source.txt" />
        </div>
      </div>
      <div class="flex items-center gap-3 mb-4 text-xs text-[var(--color-muted)]">
        <code class="text-[var(--color-accent)]">{{ copySource || 'source' }}</code>
        <span>──copy──▶</span>
        <code class="text-[var(--color-success)]">{{ copyDest || 'dest' }}</code>
      </div>
      <button class="btn btn-ghost" :disabled="copyStatus === 'loading'"
        @click="execCopy(() => ossApi.move.copy(copySource, copyDest))">
        <span v-if="copyStatus === 'loading'" class="spinner" />复制文件
      </button>
      <ResultPanel :status="copyStatus" :result="copyResult" :error="copyError" label="复制结果（void）" />
    </ApiCard>

    <!-- 移动 -->
    <ApiCard method="POST" path="/oss/object/move" summary="移动文件（复制后删除源文件）">
      <div class="grid grid-cols-2 gap-3 mb-4">
        <div>
          <p class="section-label">sourceKey（源文件 key）<span class="text-[var(--color-danger)]">*</span></p>
          <input v-model="moveSource" class="oss-input" placeholder="demo/source.txt" />
        </div>
        <div>
          <p class="section-label">destPath（目标目录路径）<span class="text-[var(--color-danger)]">*</span></p>
          <input v-model="moveDest" class="oss-input" placeholder="demo/archive/" />
        </div>
      </div>
      <div class="flex items-center gap-3 mb-4 text-xs text-[var(--color-muted)]">
        <code class="text-[var(--color-danger)] line-through">{{ moveSource || 'source' }}</code>
        <span>──move──▶</span>
        <code class="text-[var(--color-success)]">{{ moveDest || 'dest/' }}{{ (moveSource || '').split('/').pop() }}</code>
      </div>
      <button class="btn btn-ghost" :disabled="moveStatus === 'loading'"
        @click="execMove(() => ossApi.move.move(moveSource, moveDest))">
        <span v-if="moveStatus === 'loading'" class="spinner" />移动文件
      </button>
      <ResultPanel :status="moveStatus" :result="moveResult" :error="moveError" label="移动结果（void）" />
    </ApiCard>
  </div>
</template>
