<script setup lang="ts">
import { ref } from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import ResultPanel from '@/components/ResultPanel.vue'
import { useResult } from '@/composables/useResult'
import { ossApi } from '@/api/oss'

// 连接测试
const { status: connStatus, result: connResult, error: connError, execute: execConn } = useResult()
</script>

<template>
  <div>
    <h2 class="text-base font-semibold mb-1">连接测试</h2>
    <p class="text-sm text-[var(--color-muted)] mb-5">测试 OSS 连接与 Bucket 可访问性</p>

    <!-- 连接测试 -->
    <ApiCard method="GET" path="/oss/connect" summary="测试 OSS 连接与 Bucket 可访问性">
      <button class="btn btn-ghost" :disabled="connStatus === 'loading'" @click="execConn(() => ossApi.connect.test())">
        <span v-if="connStatus === 'loading'" class="spinner" />测试连接
      </button>
      <ResultPanel :status="connStatus" :result="connResult" :error="connError" label="true = 连接正常" />
    </ApiCard>
  </div>
</template>