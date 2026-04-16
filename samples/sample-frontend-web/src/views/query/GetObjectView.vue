<script setup lang="ts">
import { computed, ref } from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import ResultPanel from '@/components/ResultPanel.vue'
import { useResult } from '@/composables/useResult'
import { ossApi } from '@/api/oss'
import type { ObjectInfo } from '@/types'

// 文件详情查询需要显式区分“查到对象”和“请求成功但无详情”两种状态，
// 因为后端在对象不存在时会返回 null，而不是抛出前端可感知的错误。
const objectNameGet = ref('demo/example.txt')
const { status: getStatus, result: getResult, error: getError, execute: execGet } = useResult<ObjectInfo | null>()
const getIsEmpty = computed(() => getStatus.value === 'success' && getResult.value === null)
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
      <div v-if="getIsEmpty" class="mt-3 p-2 rounded border border-[var(--color-warning)] bg-[rgba(210,153,34,0.08)] text-[var(--color-warning)] text-xs">
        没有数据
      </div>
      <ResultPanel v-else :status="getStatus" :result="getResult" :error="getError" label="ObjectInfo" />
    </ApiCard>
  </div>
</template>
