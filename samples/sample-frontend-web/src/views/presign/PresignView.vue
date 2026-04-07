<script setup lang="ts">
import { ref } from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import ResultPanel from '@/components/ResultPanel.vue'
import { useResult } from '@/composables/useResult'
import { ossApi } from '@/api/oss'

// ── 下载预签名 URL ────────────────────────────────────────────────────
const getObject = ref('demo/example.jpg')
const getExpiry = ref(3600)
const { status: getStatus, result: getResult, error: getError, execute: execGet } = useResult()

// ── 上传预签名 URL ────────────────────────────────────────────────────
const putObject = ref('demo/upload-via-presign.jpg')
const putContentType = ref('image/jpeg')
const putExpiry = ref(3600)
const { status: putStatus, result: putResult, error: putError, execute: execPut } = useResult()

// 使用预签名 URL 上传演示
const presignFile = ref<File | null>(null)
const presignUploadStatus = ref<'idle' | 'loading' | 'success' | 'error'>('idle')
const presignUploadMsg = ref('')

async function uploadViaPresign() {
  if (!putResult.value || !presignFile.value) return
  presignUploadStatus.value = 'loading'
  try {
    await fetch(putResult.value as string, {
      method: 'PUT',
      headers: { 'Content-Type': putContentType.value },
      body: presignFile.value,
    })
    presignUploadStatus.value = 'success'
    presignUploadMsg.value = '直传成功！文件已写入 OSS'
  } catch (e: unknown) {
    presignUploadStatus.value = 'error'
    presignUploadMsg.value = e instanceof Error ? e.message : String(e)
  }
}

function copyUrl(url: unknown) {
  navigator.clipboard.writeText(String(url))
}
</script>

<template>
  <div>
    <h2 class="text-base font-semibold mb-1">预签名 URL</h2>
    <p class="text-sm text-[var(--color-muted)] mb-5">生成临时鉴权链接：GET（下载）和 PUT（前端直传，无需后端转发）</p>

    <!-- 下载预签名 -->
    <ApiCard method="GET" path="/oss/presign/get" summary="生成文件下载预签名 URL">
      <div class="grid grid-cols-2 gap-3 mb-4">
        <div>
          <p class="section-label">objectName <span class="text-[var(--color-danger)]">*</span></p>
          <input v-model="getObject" class="oss-input" placeholder="demo/example.jpg" />
        </div>
        <div>
          <p class="section-label">有效期（秒，默认 3600）</p>
          <input v-model.number="getExpiry" type="number" class="oss-input" min="1" />
        </div>
      </div>
      <button
        class="btn btn-ghost"
        :disabled="getStatus === 'loading'"
        @click="execGet(() => ossApi.presign.getUrl(getObject, getExpiry))"
      >
        <span v-if="getStatus === 'loading'" class="spinner" />生成链接
      </button>

      <div v-if="getStatus === 'success' && getResult" class="mt-4">
        <p class="section-label">预签名 URL（GET）</p>
        <div class="code-block break-all">{{ getResult }}</div>
        <div class="flex gap-2 mt-2">
          <button class="btn btn-ghost text-xs" @click="copyUrl(getResult)">复制链接</button>
          <a :href="String(getResult)" target="_blank" class="btn btn-success text-xs">在浏览器打开</a>
        </div>
      </div>
      <ResultPanel v-if="getStatus === 'error'" :status="getStatus" :result="null" :error="getError" />
    </ApiCard>

    <!-- 上传预签名 -->
    <ApiCard method="GET" path="/oss/presign/put" summary="生成文件上传预签名 URL（前端直传）">
      <div class="grid grid-cols-3 gap-3 mb-4">
        <div>
          <p class="section-label">objectName（目标 key）<span class="text-[var(--color-danger)]">*</span></p>
          <input v-model="putObject" class="oss-input" placeholder="demo/upload-via-presign.jpg" />
        </div>
        <div>
          <p class="section-label">contentType</p>
          <input v-model="putContentType" class="oss-input" placeholder="image/jpeg" />
        </div>
        <div>
          <p class="section-label">有效期（秒）</p>
          <input v-model.number="putExpiry" type="number" class="oss-input" min="1" />
        </div>
      </div>
      <button
        class="btn btn-ghost"
        :disabled="putStatus === 'loading'"
        @click="execPut(() => ossApi.presign.putUrl(putObject, putContentType, putExpiry))"
      >
        <span v-if="putStatus === 'loading'" class="spinner" />生成上传链接
      </button>

      <!-- 生成成功后，演示直传 -->
      <div v-if="putStatus === 'success' && putResult" class="mt-4">
        <p class="section-label">预签名 URL（PUT）</p>
        <div class="code-block break-all mb-3">{{ putResult }}</div>
        <button class="btn btn-ghost text-xs mb-4" @click="copyUrl(putResult)">复制链接</button>

        <div class="border-t border-[var(--color-border)] pt-4 mt-2">
          <p class="section-label text-[var(--color-accent)]">演示：使用此 URL 直传文件（浏览器 fetch PUT）</p>
          <p class="text-xs text-[var(--color-muted)] mb-3">
            选择文件后点击「直传到 OSS」，请求将直接发往 OSS，不经过后端服务器。
          </p>
          <div class="flex gap-3 items-center flex-wrap">
            <input
              type="file"
              class="oss-input w-auto"
              @change="(e) => presignFile = (e.target as HTMLInputElement).files?.[0] ?? null"
            />
            <button
              class="btn btn-primary"
              :disabled="!presignFile || presignUploadStatus === 'loading'"
              @click="uploadViaPresign"
            >
              <span v-if="presignUploadStatus === 'loading'" class="spinner" />直传到 OSS
            </button>
          </div>
          <p
            v-if="presignUploadMsg"
            class="mt-2 text-sm"
            :class="presignUploadStatus === 'success' ? 'text-[var(--color-success)]' : 'text-[var(--color-danger)]'"
          >
            {{ presignUploadMsg }}
          </p>
        </div>
      </div>
      <ResultPanel v-if="putStatus === 'error'" :status="putStatus" :result="null" :error="putError" />
    </ApiCard>
  </div>
</template>
