<script setup lang="ts">
import {ref} from 'vue'
import ResultPanel from '@/components/ResultPanel.vue'
import {useResult} from '@/composables/useResult'
import {ossApi} from '@/api/oss'
import type {LifecycleRuleInfo} from '@/types'

// 生命周期
const {
  status: getLifeStatus,
  result: getLifeResult,
  error: getLifeError,
  execute: execGetLife
} = useResult<LifecycleRuleInfo[]>()
const {status: delLifeStatus, result: delLifeResult, error: delLifeError, execute: execDelLife} = useResult()
const lifecycleRuleId = ref('expire-rule-1')
const lifecyclePrefix = ref('tmp/')
const lifecycleDays = ref(30)
const {status: addLifeStatus, result: addLifeResult, error: addLifeError, execute: execAddLife} = useResult()
</script>

<template>
  <div>
    <h2 class="text-base font-semibold mb-1">生命周期规则</h2>
    <p class="text-sm text-[var(--color-muted)] mb-5">配置对象的自动过期删除规则</p>

    <!-- 生命周期 -->
    <div class="card mb-4">
      <p class="text-sm font-medium mb-4">生命周期规则（Lifecycle）</p>
      <div class="flex gap-2 mb-4">
        <button class="btn btn-ghost" :disabled="getLifeStatus === 'loading'"
                @click="execGetLife(() => ossApi.bucket.getLifecycle())">
          <span v-if="getLifeStatus === 'loading'" class="spinner"/>GET 查询规则
        </button>
        <button class="btn btn-danger" :disabled="delLifeStatus === 'loading'"
                @click="execDelLife(() => ossApi.bucket.deleteLifecycle())">
          <span v-if="delLifeStatus === 'loading'" class="spinner"/>DELETE 删除所有
        </button>
      </div>
      <ResultPanel :status="getLifeStatus" :result="getLifeResult" :error="getLifeError" label="生命周期规则列表"/>
      <ResultPanel :status="delLifeStatus" :result="delLifeResult" :error="delLifeError" label="删除结果（void）"/>

      <div class="border-t border-[var(--color-border)] pt-4 mt-2">
        <p class="section-label">POST /bucket/lifecycle/expiration — 添加文件过期删除规则</p>
        <div class="grid grid-cols-3 gap-3 mb-3">
          <div>
            <p class="section-label">ruleId</p>
            <input v-model="lifecycleRuleId" class="oss-input" placeholder="expire-rule-1"/>
          </div>
          <div>
            <p class="section-label">prefix（路径前缀，空 = 全 Bucket）</p>
            <input v-model="lifecyclePrefix" class="oss-input" placeholder="tmp/"/>
          </div>
          <div>
            <p class="section-label">expirationDays（过期天数）</p>
            <input v-model.number="lifecycleDays" type="number" class="oss-input" min="1"/>
          </div>
        </div>
        <button class="btn btn-ghost" :disabled="addLifeStatus === 'loading'"
                @click="execAddLife(() => ossApi.bucket.addExpiration(lifecycleRuleId, lifecyclePrefix, lifecycleDays))">
          <span v-if="addLifeStatus === 'loading'" class="spinner"/>添加规则
        </button>
        <ResultPanel :status="addLifeStatus" :result="addLifeResult" :error="addLifeError" label="添加结果（void）"/>
      </div>
    </div>
  </div>
</template>
