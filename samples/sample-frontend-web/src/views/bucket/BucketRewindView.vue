<script setup lang="ts">
import { computed, ref } from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import ResultPanel from '@/components/ResultPanel.vue'
import { useResult } from '@/composables/useResult'
import { ossApi } from '@/api/oss'
import type { BucketRewindResult } from '@/types'

function buildLocalDateTimeValue(date: Date): string {
  const year = date.getFullYear()
  const month = `${date.getMonth() + 1}`.padStart(2, '0')
  const day = `${date.getDate()}`.padStart(2, '0')
  const hour = `${date.getHours()}`.padStart(2, '0')
  const minute = `${date.getMinutes()}`.padStart(2, '0')
  return `${year}-${month}-${day}T${hour}:${minute}`
}

function buildDefaultTargetTime(): string {
  return buildLocalDateTimeValue(new Date(Date.now() - 60 * 60 * 1000))
}

function toIsoTargetTime(localDateTime: string): string {
  if (!localDateTime) {
    throw new Error('请选择回滚时间')
  }
  const targetDate = new Date(localDateTime)
  if (Number.isNaN(targetDate.getTime())) {
    throw new Error('回滚时间格式不正确')
  }
  if (targetDate.getTime() > Date.now()) {
    throw new Error('回滚时间不能大于当前时间')
  }
  return targetDate.toISOString()
}

function formatUtcToLocal(utcDateTime?: string | null): string {
  if (!utcDateTime) {
    return '-'
  }
  const date = new Date(utcDateTime)
  if (Number.isNaN(date.getTime())) {
    return utcDateTime
  }
  return date.toLocaleString('zh-CN', { hour12: false })
}

// Bucket 回滚
const bucketName = ref('my-bucket')
const targetTime = ref(buildDefaultTargetTime())
const maxTargetTime = computed(() => buildLocalDateTimeValue(new Date()))
const normalizedTargetTime = computed(() => {
  if (!targetTime.value) {
    return ''
  }
  const targetDate = new Date(targetTime.value)
  return Number.isNaN(targetDate.getTime()) ? '' : targetDate.toISOString()
})
const { status, result, error, execute } = useResult<BucketRewindResult>()
</script>

<template>
  <div>
    <h2 class="text-base font-semibold mb-1">Bucket 时间回滚</h2>
    <p class="text-sm text-[var(--color-muted)] mb-5">基于版本控制，把指定 Bucket 的当前可见状态回滚到目标时间点</p>

    <ApiCard method="POST" path="/oss/buckets/{bucketName}/rewind" summary="按时间回滚指定 Bucket">
      <div class="grid grid-cols-2 gap-3 mb-4">
        <div>
          <p class="section-label">bucketName</p>
          <input v-model="bucketName" class="oss-input" placeholder="my-bucket" />
        </div>
        <div>
          <p class="section-label">targetTime</p>
          <input v-model="targetTime" type="datetime-local" class="oss-input" :max="maxTargetTime" />
        </div>
      </div>

      <div class="p-3 mb-4 rounded border border-[var(--color-border)] bg-[var(--color-bg)] text-xs text-[var(--color-muted)]">
        页面输入的是本地时间，提交给后端时会转换成 UTC 时间。北京时间比 UTC 快 8 小时，
        所以如果返回值显示为 UTC，看起来会比输入值少 8 小时，但它们表示的是同一个时间点。
      </div>

      <div class="p-3 mb-4 rounded border border-[var(--color-border)] bg-[var(--color-surface)] text-xs">
        <p class="section-label">提交到后端的 UTC 时间</p>
        <code>{{ normalizedTargetTime }}</code>
      </div>

      <button class="btn btn-danger" :disabled="status === 'loading'" @click="execute(() => ossApi.bucket.rewind(bucketName, toIsoTargetTime(targetTime)))">
        <span v-if="status === 'loading'" class="spinner" />执行回滚
      </button>

      <ResultPanel :status="status" :result="result" :error="error" label="BucketRewindResult">
        <template #success>
          <div class="card">
            <p class="section-label">BucketRewindResult</p>
            <div class="grid grid-cols-2 gap-3 text-sm">
              <div>
                <p class="section-label">Bucket</p>
                <p>{{ result?.bucketName ?? '-' }}</p>
              </div>
              <div>
                <p class="section-label">目标 UTC 时间</p>
                <p>{{ result?.targetTime ?? '-' }}</p>
              </div>
              <div>
                <p class="section-label">对应本地时间</p>
                <p>{{ formatUtcToLocal(result?.targetTime) }}</p>
              </div>
              <div>
                <p class="section-label">扫描对象数</p>
                <p>{{ result?.scannedObjectCount ?? 0 }}</p>
              </div>
              <div>
                <p class="section-label">恢复对象数</p>
                <p>{{ result?.restoredObjectCount ?? 0 }}</p>
              </div>
              <div>
                <p class="section-label">隐藏对象数</p>
                <p>{{ result?.deletedObjectCount ?? 0 }}</p>
              </div>
              <div>
                <p class="section-label">跳过对象数</p>
                <p>{{ result?.skippedObjectCount ?? 0 }}</p>
              </div>
            </div>
          </div>
        </template>
      </ResultPanel>
    </ApiCard>
  </div>
</template>
