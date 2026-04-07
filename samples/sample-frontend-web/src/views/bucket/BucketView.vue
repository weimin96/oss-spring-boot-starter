<script setup lang="ts">
import { ref } from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import ResultPanel from '@/components/ResultPanel.vue'
import { useResult } from '@/composables/useResult'
import { ossApi } from '@/api/oss'

// ── 创建 Bucket ───────────────────────────────────────────────────────
const newBucketName = ref('my-new-bucket')
const { status: createStatus, result: createResult, error: createError, execute: execCreate } = useResult()

// ── 版本控制 ──────────────────────────────────────────────────────────
const { status: getVerStatus, result: getVerResult, error: getVerError, execute: execGetVer } = useResult()
const { status: enableVerStatus, result: enableVerResult, error: enableVerError, execute: execEnableVer } = useResult()
const { status: suspendVerStatus, result: suspendVerResult, error: suspendVerError, execute: execSuspendVer } = useResult()

// ── 生命周期 ──────────────────────────────────────────────────────────
const { status: getLifeStatus, result: getLifeResult, error: getLifeError, execute: execGetLife } = useResult()
const { status: delLifeStatus, result: delLifeResult, error: delLifeError, execute: execDelLife } = useResult()
const lifecycleRuleId = ref('expire-rule-1')
const lifecyclePrefix = ref('tmp/')
const lifecycleDays = ref(30)
const { status: addLifeStatus, result: addLifeResult, error: addLifeError, execute: execAddLife } = useResult()

// ── CORS ──────────────────────────────────────────────────────────────
const { status: getCorsStatus, result: getCorsResult, error: getCorsError, execute: execGetCors } = useResult()
const { status: allowCorsStatus, result: allowCorsResult, error: allowCorsError, execute: execAllowCors } = useResult()
const { status: delCorsStatus, result: delCorsResult, error: delCorsError, execute: execDelCors } = useResult()

// ── Policy ────────────────────────────────────────────────────────────
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

// ── 安全 ──────────────────────────────────────────────────────────────
const { status: encStatus, result: encResult, error: encError, execute: execEnc } = useResult()
const { status: blockStatus, result: blockResult, error: blockError, execute: execBlock } = useResult()
</script>

<template>
  <div>
    <h2 class="text-base font-semibold mb-1">Bucket 管理</h2>
    <p class="text-sm text-[var(--color-muted)] mb-2">
      创建 Bucket、版本控制、生命周期、CORS、访问策略、安全配置
    </p>
    <p class="text-xs text-[var(--color-muted)] mb-5">
      ℹ️ 大部分写操作（创建、启用/挂起、删除等）后端返回 <code>R.success("xxx")</code>，
      <code>data</code> 字段为 null（void 语义），ResultPanel 已做处理显示「操作成功」。
    </p>

    <!-- 创建 Bucket -->
    <ApiCard method="POST" path="/oss/bucket" summary="创建 Bucket（返回 void）">
      <div class="mb-3">
        <p class="section-label">bucketName</p>
        <input v-model="newBucketName" class="oss-input" placeholder="my-new-bucket" />
      </div>
      <button class="btn btn-primary" :disabled="createStatus === 'loading'"
        @click="execCreate(() => ossApi.bucket.create(newBucketName))">
        <span v-if="createStatus === 'loading'" class="spinner" />创建 Bucket
      </button>
      <ResultPanel :status="createStatus" :result="createResult" :error="createError" label="创建结果（void）" />
    </ApiCard>

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

    <!-- 生命周期 -->
    <div class="card mb-4">
      <p class="text-sm font-medium mb-4">生命周期规则（Lifecycle）</p>
      <div class="flex gap-2 mb-4">
        <button class="btn btn-ghost" :disabled="getLifeStatus === 'loading'"
          @click="execGetLife(() => ossApi.bucket.getLifecycle())">
          <span v-if="getLifeStatus === 'loading'" class="spinner" />GET 查询规则
        </button>
        <button class="btn btn-danger" :disabled="delLifeStatus === 'loading'"
          @click="execDelLife(() => ossApi.bucket.deleteLifecycle())">
          <span v-if="delLifeStatus === 'loading'" class="spinner" />DELETE 删除所有
        </button>
      </div>
      <ResultPanel :status="getLifeStatus" :result="getLifeResult" :error="getLifeError" label="生命周期规则列表" />
      <ResultPanel :status="delLifeStatus" :result="delLifeResult" :error="delLifeError" label="删除结果（void）" />

      <div class="border-t border-[var(--color-border)] pt-4 mt-2">
        <p class="section-label">POST /bucket/lifecycle/expiration — 添加文件过期删除规则</p>
        <div class="grid grid-cols-3 gap-3 mb-3">
          <div>
            <p class="section-label">ruleId</p>
            <input v-model="lifecycleRuleId" class="oss-input" placeholder="expire-rule-1" />
          </div>
          <div>
            <p class="section-label">prefix（路径前缀，空 = 全 Bucket）</p>
            <input v-model="lifecyclePrefix" class="oss-input" placeholder="tmp/" />
          </div>
          <div>
            <p class="section-label">expirationDays（过期天数）</p>
            <input v-model.number="lifecycleDays" type="number" class="oss-input" min="1" />
          </div>
        </div>
        <button class="btn btn-ghost" :disabled="addLifeStatus === 'loading'"
          @click="execAddLife(() => ossApi.bucket.addExpiration(lifecycleRuleId, lifecyclePrefix, lifecycleDays))">
          <span v-if="addLifeStatus === 'loading'" class="spinner" />添加规则
        </button>
        <ResultPanel :status="addLifeStatus" :result="addLifeResult" :error="addLifeError" label="添加结果（void）" />
      </div>
    </div>

    <!-- CORS -->
    <div class="card mb-4">
      <p class="text-sm font-medium mb-4">跨域资源共享（CORS）</p>
      <div class="flex gap-2 mb-2 flex-wrap">
        <button class="btn btn-ghost" :disabled="getCorsStatus === 'loading'"
          @click="execGetCors(() => ossApi.bucket.getCors())">
          <span v-if="getCorsStatus === 'loading'" class="spinner" />GET 查询 CORS
        </button>
        <button class="btn btn-primary" :disabled="allowCorsStatus === 'loading'"
          @click="execAllowCors(() => ossApi.bucket.allowAllCors())">
          <span v-if="allowCorsStatus === 'loading'" class="spinner" />PUT 允许所有来源
        </button>
        <button class="btn btn-danger" :disabled="delCorsStatus === 'loading'"
          @click="execDelCors(() => ossApi.bucket.deleteCors())">
          <span v-if="delCorsStatus === 'loading'" class="spinner" />DELETE 删除 CORS
        </button>
      </div>
      <ResultPanel :status="getCorsStatus" :result="getCorsResult" :error="getCorsError" label="CORS 规则列表" />
      <ResultPanel :status="allowCorsStatus" :result="allowCorsResult" :error="allowCorsError" label="设置结果（void）" />
      <ResultPanel :status="delCorsStatus" :result="delCorsResult" :error="delCorsError" label="删除结果（void）" />
    </div>

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
