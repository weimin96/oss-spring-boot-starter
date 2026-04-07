<script setup lang="ts">
import { ref } from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import ResultPanel from '@/components/ResultPanel.vue'
import { useResult } from '@/composables/useResult'
import { ossApi } from '@/api/oss'

// 创建文件夹
const folderPath = ref('demo/newfolder/')
const { status: folderStatus, result: folderResult, error: folderError, execute: execFolder } = useResult()
</script>

<template>
  <div>
    <h2 class="text-base font-semibold mb-1">创建文件夹</h2>
    <p class="text-sm text-[var(--color-muted)] mb-5">创建目录占位符（以 / 结尾的路径）</p>

    <!-- 创建文件夹 -->
    <ApiCard method="POST" path="/oss/folder" summary="创建文件夹（目录占位符）">
      <div class="mb-4">
        <p class="section-label">文件夹路径 path <span class="text-[var(--color-danger)]">*</span></p>
        <input v-model="folderPath" class="oss-input" placeholder="demo/newfolder/" />
      </div>
      <button class="btn btn-primary" :disabled="folderStatus === 'loading'" @click="execFolder(() => ossApi.upload.createFolder(folderPath))">
        <span v-if="folderStatus === 'loading'" class="spinner" />
        <span>创建文件夹</span>
      </button>
      <ResultPanel :status="folderStatus" :result="folderResult" :error="folderError" label="创建结果 ObjectInfo" />
    </ApiCard>
  </div>
</template>