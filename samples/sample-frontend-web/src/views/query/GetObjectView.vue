<script setup lang="ts">
import { ref } from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import ResultPanel from '@/components/ResultPanel.vue'
import { useResult } from '@/composables/useResult'
import { ossApi } from '@/api/oss'

// 文件详情
const objectNameGet = ref('demo/example.txt')
const { status: getStatus, result: getResult, error: getError, execute: execGet } = useResult()
</script>

<template>
  <div>
    <h2 class="text-base font-semibold mb-1">文件详情查询</h2>
    <p class="text-sm text-[var(--color-muted)] mb-5">获取文件的元数据信息</p>

    <!-- 文件详情 -->
    <ApiCard method="GET" path="/oss/object" summary="获取文件详情（元数据）">
      <div class="mb-3">
        <p class="section-label">objectName</p>
        <input v-model="objectNameGet" class="oss-input" />
      </div>
      <button class="btn btn-ghost" :disabled="getStatus === 'loading'" @click="execGet(() => ossApi.query.getObject(objectNameGet))">
        <span v-if="getStatus === 'loading'" class="spinner" />查询
      </button>
      <ResultPanel :status="getStatus" :result="getResult" :error="getError" label="ObjectInfo" />
    </ApiCard>
  </div>
</template>