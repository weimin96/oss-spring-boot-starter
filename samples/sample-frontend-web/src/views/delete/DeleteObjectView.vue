<script setup lang="ts">
import {ref} from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import ResultPanel from '@/components/ResultPanel.vue'
import {useResult} from '@/composables/useResult'
import {ossApi} from '@/api/oss'

// 单个删除
const delKey = ref('demo/example.txt')
const {status: delStatus, result: delResult, error: delError, execute: execDel} = useResult()
</script>

<template>
  <div>
    <h2 class="text-base font-semibold mb-1">删除单个文件</h2>
    <p class="text-sm text-[var(--color-muted)] mb-5">删除 OSS 中的单个文件</p>

    <!-- 单个删除 -->
    <ApiCard method="DELETE" path="/oss/object" summary="删除单个文件">
      <div class="mb-4">
        <p class="section-label">objectName（文件全路径 key）<span class="text-[var(--color-danger)]">*</span></p>
        <input v-model="delKey" class="oss-input" placeholder="demo/example.txt"/>
      </div>
      <button
          class="btn btn-danger"
          :disabled="delStatus === 'loading'"
          @click="execDel(() => ossApi.delete.deleteObject(delKey))"
      >
        <span v-if="delStatus === 'loading'" class="spinner"/>删除文件
      </button>
      <ResultPanel :status="delStatus" :result="delResult" :error="delError" label="删除结果"/>
    </ApiCard>
  </div>
</template>