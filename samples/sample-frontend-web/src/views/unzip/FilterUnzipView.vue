<script setup lang="ts">
import { ref } from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import ResultPanel from '@/components/ResultPanel.vue'
import { useResult } from '@/composables/useResult'
import { ossApi } from '@/api/oss'

// 过滤解压
const filterZipKey = ref('demo/archive.zip')
const entryPrefix = ref('images/')
const filterTargetPath = ref('demo/images-only/')
const { status: filterStatus, result: filterResult, error: filterError, execute: execFilter } = useResult()
</script>

<template>
  <div>
    <h2 class="text-base font-semibold mb-1">过滤解压</h2>
    <p class="text-sm text-[var(--color-muted)] mb-5">
      按路径前缀过滤，只解压 ZIP 中的部分文件
    </p>

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