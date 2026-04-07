<script setup lang="ts">
import { ref } from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import ResultPanel from '@/components/ResultPanel.vue'
import { useResult } from '@/composables/useResult'
import { ossApi } from '@/api/oss'

// 版本控制
const { status: getVerStatus, result: getVerResult, error: getVerError, execute: execGetVer } = useResult()
const { status: enableVerStatus, result: enableVerResult, error: enableVerError, execute: execEnableVer } = useResult()
const { status: suspendVerStatus, result: suspendVerResult, error: suspendVerError, execute: execSuspendVer } = useResult()
</script>

<template>
  <div>
    <h2 class="text-base font-semibold mb-1">版本控制</h2>
    <p class="text-sm text-[var(--color-muted)] mb-5">开启或关闭 Bucket 的版本控制功能，保留对象的历史版本</p>

    <!-- 版本控制 -->
    <div class="card mb-4">
      <p class="text-sm font-medium mb-4">版本控制（Versioning）</p>
      <div class="flex gap-2 flex-wrap mb-2">
        <button class="btn btn-ghost" :disabled="getVerStatus === 'loading'"
          @click="execGetVer(() => ossApi.bucket.getVersioning())">
          <span v-if="getVerStatus === 'loading'" class="spinner" />GET 查询状态
        </button>
        <button class="btn btn-success" :disabled="enableVerStatus === 'loading'"
          @click="execEnableVer(() => ossApi.bucket.enableVersioning())">
          <span v-if="enableVerStatus === 'loading'" class="spinner" />PUT 启用
        </button>
        <button class="btn btn-ghost" :disabled="suspendVerStatus === 'loading'"
          @click="execSuspendVer(() => ossApi.bucket.suspendVersioning())">
          <span v-if="suspendVerStatus === 'loading'" class="spinner" />PUT 挂起
        </button>
      </div>
      <ResultPanel :status="getVerStatus" :result="getVerResult" :error="getVerError" label="版本控制状态（Enabled / Suspended / 空）" />
      <ResultPanel :status="enableVerStatus" :result="enableVerResult" :error="enableVerError" label="启用结果（void）" />
      <ResultPanel :status="suspendVerStatus" :result="suspendVerResult" :error="suspendVerError" label="挂起结果（void）" />
    </div>
  </div>
</template>