<script setup lang="ts">
import {ref} from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import ResultPanel from '@/components/ResultPanel.vue'
import {useResult} from '@/composables/useResult'
import {ossApi} from '@/api/oss'

// 跨 Bucket 解压
const sourceBucket = ref('source-bucket')
const crossZipKey = ref('archives/data.zip')
const targetBucket = ref('target-bucket')
const crossTargetPath = ref('extracted/')
const {status: crossStatus, result: crossResult, error: crossError, execute: execCross} = useResult()
</script>

<template>
  <div>
    <h2 class="text-base font-semibold mb-1">跨 Bucket 流式解压</h2>
    <p class="text-sm text-[var(--color-muted)] mb-5">
      将一个 Bucket 中的 ZIP 文件解压到另一个 Bucket
    </p>

    <!-- 跨 Bucket 解压 -->
    <ApiCard method="POST" path="/oss/unzip/cross-bucket" summary="跨 Bucket 流式解压">
      <div class="grid grid-cols-2 gap-3 mb-4">
        <div>
          <p class="section-label">sourceBucket</p>
          <input v-model="sourceBucket" class="oss-input" placeholder="source-bucket"/>
        </div>
        <div>
          <p class="section-label">zipObjectKey</p>
          <input v-model="crossZipKey" class="oss-input" placeholder="archives/data.zip"/>
        </div>
        <div>
          <p class="section-label">targetBucket</p>
          <input v-model="targetBucket" class="oss-input" placeholder="target-bucket"/>
        </div>
        <div>
          <p class="section-label">targetPath</p>
          <input v-model="crossTargetPath" class="oss-input" placeholder="extracted/"/>
        </div>
      </div>

      <!-- 流程示意 -->
      <div
          class="flex items-center gap-3 mb-4 text-xs text-[var(--color-muted)] p-3 bg-[var(--color-bg)] rounded border border-[var(--color-border)]">
        <span class="text-[var(--color-accent)]">{{ sourceBucket }}/{{ crossZipKey }}</span>
        <span>──▶ 解压 ──▶</span>
        <span class="text-[var(--color-success)]">{{ targetBucket }}/{{ crossTargetPath }}</span>
      </div>

      <button
          class="btn btn-primary"
          :disabled="crossStatus === 'loading'"
          @click="execCross(() => ossApi.unzip.crossBucket(sourceBucket, crossZipKey, targetBucket, crossTargetPath))"
      >
        <span v-if="crossStatus === 'loading'" class="spinner"/>跨 Bucket 解压
      </button>
      <ResultPanel :status="crossStatus" :result="crossResult" :error="crossError" label="UnzipResult"/>
    </ApiCard>
  </div>
</template>