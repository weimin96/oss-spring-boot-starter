<script setup lang="ts">
import { ref } from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import ResultPanel from '@/components/ResultPanel.vue'
import { useResult } from '@/composables/useResult'
import { ossApi } from '@/api/oss'

// ── 普通解压 ──────────────────────────────────────────────────────────
const zipKey = ref('demo/archive.zip')
const targetPath = ref('demo/extracted/')
const { status: unzipStatus, result: unzipResult, error: unzipError, execute: execUnzip } = useResult()

// ── 跨 Bucket 解压 ────────────────────────────────────────────────────
const sourceBucket = ref('source-bucket')
const crossZipKey = ref('archives/data.zip')
const targetBucket = ref('target-bucket')
const crossTargetPath = ref('extracted/')
const { status: crossStatus, result: crossResult, error: crossError, execute: execCross } = useResult()

// ── 过滤解压 ──────────────────────────────────────────────────────────
const filterZipKey = ref('demo/archive.zip')
const entryPrefix = ref('images/')
const filterTargetPath = ref('demo/images-only/')
const { status: filterStatus, result: filterResult, error: filterError, execute: execFilter } = useResult()
</script>

<template>
  <div>
    <h2 class="text-base font-semibold mb-1">流式解压（Unzip）</h2>
    <p class="text-sm text-[var(--color-muted)] mb-5">
      将 OSS 中的 ZIP 文件边下载边解压，直接写入目标路径，无需落本地磁盘。
    </p>

    <!-- 普通解压 -->
    <ApiCard method="POST" path="/oss/unzip" summary="流式解压 ZIP 文件">
      <div class="grid grid-cols-2 gap-3 mb-4">
        <div>
          <p class="section-label">zipObjectKey（ZIP 文件 key）<span class="text-[var(--color-danger)]">*</span></p>
          <input v-model="zipKey" class="oss-input" placeholder="demo/archive.zip" />
        </div>
        <div>
          <p class="section-label">targetPath（解压目标路径）<span class="text-[var(--color-danger)]">*</span></p>
          <input v-model="targetPath" class="oss-input" placeholder="demo/extracted/" />
        </div>
      </div>

      <!-- 流程示意 -->
      <div class="flex items-center gap-3 mb-4 text-xs text-[var(--color-muted)] p-3 bg-[var(--color-bg)] rounded border border-[var(--color-border)]">
        <span class="text-[var(--color-accent)]">OSS: {{ zipKey || 'xxx.zip' }}</span>
        <span>──stream──▶ 解压 ──▶</span>
        <span class="text-[var(--color-success)]">OSS: {{ targetPath || 'extracted/' }}</span>
        <span class="ml-2 badge badge-info">不落盘</span>
      </div>

      <button
        class="btn btn-primary"
        :disabled="unzipStatus === 'loading'"
        @click="execUnzip(() => ossApi.unzip.unzip(zipKey, targetPath))"
      >
        <span v-if="unzipStatus === 'loading'" class="spinner" />开始解压
      </button>
      <ResultPanel :status="unzipStatus" :result="unzipResult" :error="unzipError" label="UnzipResult" />
    </ApiCard>

    <!-- 跨 Bucket 解压 -->
    <ApiCard method="POST" path="/oss/unzip/cross-bucket" summary="跨 Bucket 流式解压">
      <div class="grid grid-cols-2 gap-3 mb-4">
        <div>
          <p class="section-label">sourceBucket</p>
          <input v-model="sourceBucket" class="oss-input" placeholder="source-bucket" />
        </div>
        <div>
          <p class="section-label">zipObjectKey</p>
          <input v-model="crossZipKey" class="oss-input" placeholder="archives/data.zip" />
        </div>
        <div>
          <p class="section-label">targetBucket</p>
          <input v-model="targetBucket" class="oss-input" placeholder="target-bucket" />
        </div>
        <div>
          <p class="section-label">targetPath</p>
          <input v-model="crossTargetPath" class="oss-input" placeholder="extracted/" />
        </div>
      </div>

      <!-- 流程示意 -->
      <div class="flex items-center gap-3 mb-4 text-xs text-[var(--color-muted)] p-3 bg-[var(--color-bg)] rounded border border-[var(--color-border)]">
        <span class="text-[var(--color-accent)]">{{ sourceBucket }}/{{ crossZipKey }}</span>
        <span>──▶ 解压 ──▶</span>
        <span class="text-[var(--color-success)]">{{ targetBucket }}/{{ crossTargetPath }}</span>
      </div>

      <button
        class="btn btn-primary"
        :disabled="crossStatus === 'loading'"
        @click="execCross(() => ossApi.unzip.crossBucket(sourceBucket, crossZipKey, targetBucket, crossTargetPath))"
      >
        <span v-if="crossStatus === 'loading'" class="spinner" />跨 Bucket 解压
      </button>
      <ResultPanel :status="crossStatus" :result="crossResult" :error="crossError" label="UnzipResult" />
    </ApiCard>

    <!-- 过滤解压 -->
    <ApiCard method="POST" path="/oss/unzip/filter" summary="按路径前缀过滤解压（只解压部分文件）">
      <div class="grid grid-cols-3 gap-3 mb-4">
        <div>
          <p class="section-label">zipObjectKey</p>
          <input v-model="filterZipKey" class="oss-input" placeholder="demo/archive.zip" />
        </div>
        <div>
          <p class="section-label">entryPrefix（ZIP 内路径前缀，空=全部）</p>
          <input v-model="entryPrefix" class="oss-input" placeholder="images/" />
        </div>
        <div>
          <p class="section-label">targetPath</p>
          <input v-model="filterTargetPath" class="oss-input" placeholder="demo/images-only/" />
        </div>
      </div>

      <div class="p-3 mb-4 bg-[var(--color-bg)] rounded border border-[var(--color-border)] text-xs text-[var(--color-muted)]">
        <p class="mb-1">示例：ZIP 包结构</p>
        <p>
          <code class="text-[var(--color-muted)]">archive.zip/</code><br />
          <code :class="entryPrefix ? 'text-[var(--color-success)]' : 'text-[var(--color-muted)]'">  {{ entryPrefix || 'images/' }}photo1.jpg  ← 会解压</code><br />
          <code class="text-[var(--color-danger)]">  docs/readme.md  ← 跳过</code>
        </p>
      </div>

      <button
        class="btn btn-primary"
        :disabled="filterStatus === 'loading'"
        @click="execFilter(() => ossApi.unzip.withFilter(filterZipKey, entryPrefix, filterTargetPath))"
      >
        <span v-if="filterStatus === 'loading'" class="spinner" />过滤解压
      </button>
      <ResultPanel :status="filterStatus" :result="filterResult" :error="filterError" label="UnzipResult" />
    </ApiCard>
  </div>
</template>
