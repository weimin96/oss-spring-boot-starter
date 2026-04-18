<script setup lang="ts">
import {ref} from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import ResultPanel from '@/components/ResultPanel.vue'
import {useResult} from '@/composables/useResult'
import {ossApi} from '@/api/oss'

// 复制
const copySource = ref('demo/source.txt')
const copyDest = ref('demo/backup/source.txt')
const {status: copyStatus, result: copyResult, error: copyError, execute: execCopy} = useResult()
</script>

<template>
  <div>
    <h2 class="text-base font-semibold mb-1">复制文件</h2>
    <p class="text-sm text-[var(--color-muted)] mb-2">在同一个 Bucket 内复制文件</p>

    <!-- 复制 -->
    <ApiCard method="POST" path="/oss/object/copy" summary="复制文件（同 Bucket 内）">
      <div class="grid grid-cols-2 gap-3 mb-4">
        <div>
          <p class="section-label">sourceKey（源文件 key）<span class="text-[var(--color-danger)]">*</span></p>
          <input v-model="copySource" class="oss-input" placeholder="demo/source.txt"/>
        </div>
        <div>
          <p class="section-label">destKey（目标文件 key）<span class="text-[var(--color-danger)]">*</span></p>
          <input v-model="copyDest" class="oss-input" placeholder="demo/backup/source.txt"/>
        </div>
      </div>
      <div class="flex items-center gap-3 mb-4 text-xs text-[var(--color-muted)]">
        <code class="text-[var(--color-accent)]">{{ copySource || 'source' }}</code>
        <span>──copy──▶</span>
        <code class="text-[var(--color-success)]">{{ copyDest || 'dest' }}</code>
      </div>
      <button class="btn btn-ghost" :disabled="copyStatus === 'loading'"
              @click="execCopy(() => ossApi.move.copy(copySource, copyDest))">
        <span v-if="copyStatus === 'loading'" class="spinner"/>复制文件
      </button>
      <ResultPanel :status="copyStatus" :result="copyResult" :error="copyError" label="复制结果（void）"/>
    </ApiCard>
  </div>
</template>