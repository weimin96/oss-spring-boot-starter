<script setup lang="ts">
import { ref } from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import ResultPanel from '@/components/ResultPanel.vue'
import { useResult } from '@/composables/useResult'
import { ossApi } from '@/api/oss'

// 安全
const { status: encStatus, result: encResult, error: encError, execute: execEnc } = useResult()
const { status: blockStatus, result: blockResult, error: blockError, execute: execBlock } = useResult()
</script>

<template>
  <div>
    <h2 class="text-base font-semibold mb-1">安全配置</h2>
    <p class="text-sm text-[var(--color-muted)] mb-2">Bucket 的安全相关配置</p>
    <p class="text-xs text-[var(--color-muted)] mb-5">
      ℹ️ 大部分写操作后端返回 <code>R.success("xxx")</code>，
      <code>data</code> 字段为 null（void 语义），ResultPanel 已做处理显示「操作成功」。
    </p>

    <!-- 安全配置 -->
    <div class="card mb-4">
      <p class="text-sm font-medium mb-2">安全配置</p>
      <p class="text-xs text-[var(--color-muted)] mb-4">这些操作会修改 Bucket 安全设置，请在了解影响后执行。</p>
      <div class="p-3 mb-3 rounded border border-[var(--color-warning)] bg-[rgba(210,153,34,0.08)] text-[var(--color-warning)] text-xs">
        ⚠️ 「屏蔽公共访问」一旦开启，所有公开读取策略将被阻止，可能影响已有业务。
      </div>
      <div class="flex gap-2 flex-wrap">
        <button class="btn btn-ghost" :disabled="encStatus === 'loading'"
          @click="execEnc(() => ossApi.bucket.enableEncryption())">
          <span v-if="encStatus === 'loading'" class="spinner" />启用服务端加密（SSE-S3）
        </button>
        <button class="btn btn-danger" :disabled="blockStatus === 'loading'"
          @click="execBlock(() => ossApi.bucket.blockPublicAccess())">
          <span v-if="blockStatus === 'loading'" class="spinner" />屏蔽所有公共访问
        </button>
      </div>
      <ResultPanel :status="encStatus" :result="encResult" :error="encError" label="加密启用结果（void）" />
      <ResultPanel :status="blockStatus" :result="blockResult" :error="blockError" label="屏蔽结果（void）" />
    </div>
  </div>
</template>