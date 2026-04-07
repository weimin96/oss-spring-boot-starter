<script setup lang="ts">
import { ref } from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import ResultPanel from '@/components/ResultPanel.vue'
import { useResult } from '@/composables/useResult'
import { ossApi } from '@/api/oss'

// ── 单文件上传 ────────────────────────────────────────────────────────
const uploadFile = ref<File | null>(null)
const uploadPath = ref('demo/')
const uploadFilename = ref('')
const uploadProgress = ref(0)
const isDragOver = ref(false)
const { status: uploadStatus, result: uploadResult, error: uploadError, execute: execUpload } = useResult()

function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  uploadFile.value = input.files?.[0] ?? null
  uploadProgress.value = 0
}

function onDrop(e: DragEvent) {
  isDragOver.value = false
  uploadFile.value = e.dataTransfer?.files[0] ?? null
  uploadProgress.value = 0
}

async function doUpload() {
  if (!uploadFile.value) return
  uploadProgress.value = 0
  await execUpload(() =>
    ossApi.upload.putObject(
      uploadFile.value!,
      uploadPath.value,
      uploadFilename.value || undefined,
      (pct) => { uploadProgress.value = pct },
    ),
  )
}

// ── 创建文件夹 ────────────────────────────────────────────────────────
const folderPath = ref('demo/newfolder/')
const { status: folderStatus, result: folderResult, error: folderError, execute: execFolder } = useResult()
</script>

<template>
  <div>
    <h2 class="text-base font-semibold mb-1">文件上传</h2>
    <p class="text-sm text-[var(--color-muted)] mb-5">单文件上传 &amp; 创建文件夹（目录占位符）</p>

    <!-- 单文件上传 -->
    <ApiCard method="POST" path="/oss/object" summary="上传文件（单文件）">
      <!-- 拖拽区 -->
      <div
        class="upload-zone mb-4"
        :class="{ 'drag-over': isDragOver }"
        @dragover.prevent="isDragOver = true"
        @dragleave="isDragOver = false"
        @drop.prevent="onDrop"
        @click="($refs.fileInput as HTMLInputElement).click()"
      >
        <input ref="fileInput" type="file" class="hidden" @change="onFileChange" />
        <div v-if="uploadFile" class="text-sm">
          <p class="font-medium text-[var(--color-text)]">{{ uploadFile.name }}</p>
          <p class="text-xs text-[var(--color-muted)] mt-1">{{ (uploadFile.size / 1024).toFixed(1) }} KB</p>
        </div>
        <div v-else>
          <p class="text-sm">点击选择文件，或拖拽到此处</p>
          <p class="text-xs mt-1 opacity-60">支持任意格式</p>
        </div>
      </div>

      <!-- 参数 -->
      <div class="grid grid-cols-2 gap-3 mb-4">
        <div>
          <p class="section-label">存放路径 path <span class="text-[var(--color-danger)]">*</span></p>
          <input v-model="uploadPath" class="oss-input" placeholder="demo/" />
        </div>
        <div>
          <p class="section-label">自定义文件名 filename</p>
          <input v-model="uploadFilename" class="oss-input" placeholder="（留空使用原文件名）" />
        </div>
      </div>

      <!-- 进度条 -->
      <div v-if="uploadStatus === 'loading'" class="mb-3">
        <div class="progress-track">
          <div class="progress-fill" :style="{ width: `${uploadProgress}%` }" />
        </div>
        <p class="text-xs text-[var(--color-muted)] mt-1">{{ uploadProgress }}%</p>
      </div>

      <button class="btn btn-primary" :disabled="!uploadFile || uploadStatus === 'loading'" @click="doUpload">
        <span v-if="uploadStatus === 'loading'" class="spinner" />
        <span>上传文件</span>
      </button>

      <ResultPanel :status="uploadStatus" :result="uploadResult" :error="uploadError" label="上传结果 ObjectInfo" />
    </ApiCard>

    <!-- 创建文件夹 -->
    <ApiCard method="POST" path="/oss/folder" summary="创建文件夹（目录占位符）">
      <div class="mb-4">
        <p class="section-label">文件夹路径 path <span class="text-[var(--color-danger)]">*</span></p>
        <input v-model="folderPath" class="oss-input" placeholder="demo/newfolder/" />
      </div>
      <button class="btn btn-primary" :disabled="folderStatus === 'loading'" @click="execFolder(() => ossApi.upload.createFolder(folderPath))">
        <span v-if="folderStatus === 'loading'" class="spinner" />
        <span>创建文件夹</span>
      </button>
      <ResultPanel :status="folderStatus" :result="folderResult" :error="folderError" label="创建结果 ObjectInfo" />
    </ApiCard>
  </div>
</template>
