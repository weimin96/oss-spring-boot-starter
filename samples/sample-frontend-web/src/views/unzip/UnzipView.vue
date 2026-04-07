<script setup lang="ts">
import { ref } from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import ResultPanel from '@/components/ResultPanel.vue'
import { useResult } from '@/composables/useResult'
import { ossApi } from '@/api/oss'

// 普通解压
const zipKey = ref('demo/archive.zip')
const targetPath = ref('demo/extracted/')
const { status: unzipStatus, result: unzipResult, error: unzipError, execute: execUnzip } = useResult()
</script>

<template>
  <div>
    <h2 class="text-base font-semibold mb-1">流式解压</h2>
    <p class="text-sm text-[var(--color-muted)] mb-5">
      将 OSS 中的 ZIP 文件边下载边解压，直接写入目标路径，无需落本地磁盘。
    </p>

    <!-- 普通解压 -->
    <ApiCard method="POST" path="/oss/unzip" summary="流式解压 ZIP 文件">
      <div class="grid grid-cols-2 gap-3 mb-4">
        <div>
          <p class="section-label">zipObjectKey（ZIP 文件 key）<span class="text-[var(--color-danger)]">*</span></p>
          <input v-model="zipKey" class="oss-input" placeholder="demo/archive.zip" />
        </div>
        <div>
          <p class="section-label">targetPath（解压目标路径）<span class="text-[var(--color-danger)]">*</span></p>
          <input v-model="targetPath" class="oss-input" placeholder="demo/extracted/" />
        </div>
      </div>

      <!-- 流程示意 -->
      <div class="flex items-center gap-3 mb-4 text-xs text-[var(--color-muted)] p-3 bg-[var(--color-bg)] rounded border border-[var(--color-border)]">
        <span class="text-[var(--color-accent)]">OSS: {{ zipKey || 'xxx.zip' }}</span>
        <span>──stream──▶ 解压 ──▶</span>
        <span class="text-[var(--color-success)]">OSS: {{ targetPath || 'extracted/' }}</span>
        <span class="ml-2 badge badge-info">不落盘</span>
      </div>

      <button
        class="btn btn-primary"
        :disabled="unzipStatus === 'loading'"
        @click="execUnzip(() => ossApi.unzip.unzip(zipKey, targetPath))"
      >
        <span v-if="unzipStatus === 'loading'" class="spinner" />开始解压
      </button>
      <ResultPanel :status="unzipStatus" :result="unzipResult" :error="unzipError" label="UnzipResult" />
    </ApiCard>
  </div>
</template>