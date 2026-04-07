<script setup lang="ts">
import { ref } from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import ResultPanel from '@/components/ResultPanel.vue'
import { useResult } from '@/composables/useResult'
import { ossApi } from '@/api/oss'

// Policy
const { status: getPolicyStatus, result: getPolicyResult, error: getPolicyError, execute: execGetPolicy } = useResult()
const policyJson = ref(JSON.stringify({
  Version: '2012-10-17',
  Statement: [{
    Effect: 'Allow',
    Principal: '*',
    Action: ['s3:GetObject'],
    Resource: ['arn:aws:s3:::my-bucket/*'],
  }],
}, null, 2))
const { status: putPolicyStatus, result: putPolicyResult, error: putPolicyError, execute: execPutPolicy } = useResult()
const { status: delPolicyStatus, result: delPolicyResult, error: delPolicyError, execute: execDelPolicy } = useResult()
</script>

<template>
  <div>
    <h2 class="text-base font-semibold mb-1">访问策略</h2>
    <p class="text-sm text-[var(--color-muted)] mb-5">配置 Bucket Policy（IAM Policy 格式），控制资源访问权限</p>

    <!-- Policy -->
    <div class="card mb-4">
      <p class="text-sm font-medium mb-4">访问策略（Bucket Policy）</p>
      <button class="btn btn-ghost mb-3" :disabled="getPolicyStatus === 'loading'"
        @click="execGetPolicy(() => ossApi.bucket.getPolicy())">
        <span v-if="getPolicyStatus === 'loading'" class="spinner" />GET 查询策略
      </button>
      <ResultPanel :status="getPolicyStatus" :result="getPolicyResult" :error="getPolicyError" label="Policy JSON" />

      <div class="border-t border-[var(--color-border)] pt-4 mt-3">
        <p class="section-label">PUT 设置策略（IAM Policy JSON）</p>
        <textarea
          v-model="policyJson"
          class="oss-input mb-3"
          rows="8"
          style="resize: vertical; font-size: 12px;"
        />
        <div class="flex gap-2">
          <button class="btn btn-primary" :disabled="putPolicyStatus === 'loading'"
            @click="execPutPolicy(() => ossApi.bucket.putPolicy(policyJson))">
            <span v-if="putPolicyStatus === 'loading'" class="spinner" />PUT 设置策略
          </button>
          <button class="btn btn-danger" :disabled="delPolicyStatus === 'loading'"
            @click="execDelPolicy(() => ossApi.bucket.deletePolicy())">
            <span v-if="delPolicyStatus === 'loading'" class="spinner" />DELETE 删除策略
          </button>
        </div>
        <ResultPanel :status="putPolicyStatus" :result="putPolicyResult" :error="putPolicyError" label="PUT 结果（void）" />
        <ResultPanel :status="delPolicyStatus" :result="delPolicyResult" :error="delPolicyError" label="DELETE 结果（void）" />
      </div>
    </div>
  </div>
</template>