<script setup lang="ts">
import {ref} from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import ResultPanel from '@/components/ResultPanel.vue'
import {useResult} from '@/composables/useResult'
import {ossApi} from '@/api/oss'

// 文件存在检查
const objectNameExists = ref('demo/example.txt')
const {status: existsStatus, result: existsResult, error: existsError, execute: execExists} = useResult()
</script>

<template>
  <div>
    <h2 class="text-base font-semibold mb-1">文件存在检查</h2>
    <p class="text-sm text-[var(--color-muted)] mb-5">检查指定文件是否存在于 OSS 中</p>

    <!-- 文件是否存在 -->
    <ApiCard method="GET" path="/oss/object/exists" summary="检查文件是否存在">
      <div class="mb-3">
        <p class="section-label">objectName</p>
        <input v-model="objectNameExists" class="oss-input"/>
      </div>
      <button class="btn btn-ghost" :disabled="existsStatus === 'loading'"
              @click="execExists(() => ossApi.query.exists(objectNameExists))">
        <span v-if="existsStatus === 'loading'" class="spinner"/>检查
      </button>
      <ResultPanel :status="existsStatus" :result="existsResult" :error="existsError" label="boolean"/>
    </ApiCard>
  </div>
</template>