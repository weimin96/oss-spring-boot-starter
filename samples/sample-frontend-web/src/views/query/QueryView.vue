<script setup lang="ts">
import { ref } from 'vue'
import ApiCard from '@/components/ApiCard.vue'
import ResultPanel from '@/components/ResultPanel.vue'
import { useResult } from '@/composables/useResult'
import { ossApi } from '@/api/oss'

// ── 连接测试 ──────────────────────────────────────────────────────────
const { status: connStatus, result: connResult, error: connError, execute: execConn } = useResult()

// ── 文件详情 ──────────────────────────────────────────────────────────
const objectNameGet = ref('demo/example.txt')
const { status: getStatus, result: getResult, error: getError, execute: execGet } = useResult()

// ── 文件存在检查 ──────────────────────────────────────────────────────
const objectNameExists = ref('demo/example.txt')
const { status: existsStatus, result: existsResult, error: existsError, execute: execExists } = useResult()

// ── 列举对象 ──────────────────────────────────────────────────────────
const listPath = ref('demo/')
const { status: listStatus, result: listResult, error: listError, execute: execList } = useResult()

// ── 下一层级 ──────────────────────────────────────────────────────────
const nextLevelPath = ref('demo/')
const { status: nextStatus, result: nextResult, error: nextError, execute: execNext } = useResult()

// ── 懒加载 ───────────────────────────────────────────────────────────
const lazyPath = ref('demo/')
const lazyMaxKeys = ref(10)
const lazyToken = ref('')
const { status: lazyStatus, result: lazyResult, error: lazyError, execute: execLazy } = useResult()

// ── 目录树 ───────────────────────────────────────────────────────────
const treePath = ref('demo/')
const { status: treeStatus, result: treeResult, error: treeError, execute: execTree } = useResult()

// ── 关键字搜索树 ──────────────────────────────────────────────────────
const searchPath = ref('demo/')
const searchKeyword = ref('image')
const { status: searchStatus, result: searchResult, error: searchError, execute: execSearch } = useResult()

// ── 文件夹树 ──────────────────────────────────────────────────────────
const folderTreePath = ref('demo/')
const { status: folderTreeStatus, result: folderTreeResult, error: folderTreeError, execute: execFolderTree } = useResult()

// ── Bucket 列表 ───────────────────────────────────────────────────────
const { status: bucketsStatus, result: bucketsResult, error: bucketsError, execute: execBuckets } = useResult()

// ── 预览 / 下载 ───────────────────────────────────────────────────────
const previewKey = ref('demo/example.jpg')
const downloadKey = ref('demo/example.txt')

async function previewFile() {
  const res = await ossApi.query.preview(previewKey.value)
  const url = URL.createObjectURL(res.data)
  window.open(url, '_blank')
}
async function downloadFile() {
  const res = await ossApi.query.download(downloadKey.value)
  const url = URL.createObjectURL(res.data)
  const a = document.createElement('a')
  a.href = url
  a.download = downloadKey.value.split('/').pop() ?? 'file'
  a.click()
}
</script>

<template>
  <div>
    <h2 class="text-base font-semibold mb-1">文件查询</h2>
    <p class="text-sm text-[var(--color-muted)] mb-5">连接测试、文件详情、列举、目录树、懒加载、预览下载</p>

    <!-- 连接测试 -->
    <ApiCard method="GET" path="/oss/connect" summary="测试 OSS 连接与 Bucket 可访问性">
      <button class="btn btn-ghost" :disabled="connStatus === 'loading'" @click="execConn(() => ossApi.connect.test())">
        <span v-if="connStatus === 'loading'" class="spinner" />
        <span>测试连接</span>
      </button>
      <ResultPanel :status="connStatus" :result="connResult" :error="connError" label="连接结果（true = 正常）" />
    </ApiCard>

    <!-- 文件详情 -->
    <ApiCard method="GET" path="/oss/object" summary="获取文件详情（元数据）">
      <div class="mb-3">
        <p class="section-label">objectName</p>
        <input v-model="objectNameGet" class="oss-input" />
      </div>
      <button class="btn btn-ghost" :disabled="getStatus === 'loading'" @click="execGet(() => ossApi.query.getObject(objectNameGet))">
        <span v-if="getStatus === 'loading'" class="spinner" />查询
      </button>
      <ResultPanel :status="getStatus" :result="getResult" :error="getError" label="ObjectInfo" />
    </ApiCard>

    <!-- 文件是否存在 -->
    <ApiCard method="GET" path="/oss/object/exists" summary="检查文件是否存在">
      <div class="mb-3">
        <p class="section-label">objectName</p>
        <input v-model="objectNameExists" class="oss-input" />
      </div>
      <button class="btn btn-ghost" :disabled="existsStatus === 'loading'" @click="execExists(() => ossApi.query.exists(objectNameExists))">
        <span v-if="existsStatus === 'loading'" class="spinner" />检查
      </button>
      <ResultPanel :status="existsStatus" :result="existsResult" :error="existsError" label="是否存在（boolean）" />
    </ApiCard>

    <!-- 列举对象 -->
    <ApiCard method="GET" path="/oss/object/list" summary="列举指定路径下所有对象（含子路径）">
      <div class="mb-3">
        <p class="section-label">path</p>
        <input v-model="listPath" class="oss-input" placeholder="demo/" />
      </div>
      <button class="btn btn-ghost" :disabled="listStatus === 'loading'" @click="execList(() => ossApi.query.listObjects(listPath))">
        <span v-if="listStatus === 'loading'" class="spinner" />列举
      </button>
      <ResultPanel :status="listStatus" :result="listResult" :error="listError" label="ObjectInfo[]" />
    </ApiCard>

    <!-- 下一层级 -->
    <ApiCard method="GET" path="/oss/object/list/next-level" summary="列举下一层级的文件和文件夹">
      <div class="mb-3">
        <p class="section-label">path</p>
        <input v-model="nextLevelPath" class="oss-input" placeholder="demo/" />
      </div>
      <button class="btn btn-ghost" :disabled="nextStatus === 'loading'" @click="execNext(() => ossApi.query.listNextLevel(nextLevelPath))">
        <span v-if="nextStatus === 'loading'" class="spinner" />查询
      </button>
      <ResultPanel :status="nextStatus" :result="nextResult" :error="nextError" label="ObjectTreeNode[]" />
    </ApiCard>

    <!-- 懒加载分页 -->
    <ApiCard method="GET" path="/oss/object/list/lazy" summary="懒加载列表（分页 / 断点翻页）">
      <div class="grid grid-cols-3 gap-3 mb-3">
        <div>
          <p class="section-label">path</p>
          <input v-model="lazyPath" class="oss-input" placeholder="demo/" />
        </div>
        <div>
          <p class="section-label">maxKeys</p>
          <input v-model.number="lazyMaxKeys" type="number" class="oss-input" min="1" />
        </div>
        <div>
          <p class="section-label">continuationToken（翻页）</p>
          <input v-model="lazyToken" class="oss-input" placeholder="（首次留空）" />
        </div>
      </div>
      <button class="btn btn-ghost" :disabled="lazyStatus === 'loading'" @click="execLazy(() => ossApi.query.lazyList(lazyPath, lazyMaxKeys, lazyToken || undefined))">
        <span v-if="lazyStatus === 'loading'" class="spinner" />加载
      </button>
      <p v-if="(lazyResult as any)?.nextContinuationToken" class="mt-2 text-xs text-[var(--color-muted)]">
        下一页 token：
        <code class="text-[var(--color-accent)] cursor-pointer" @click="lazyToken = (lazyResult as any).nextContinuationToken">
          {{ (lazyResult as any).nextContinuationToken }}
        </code>
        <span class="text-[var(--color-accent)] ml-2 cursor-pointer" @click="lazyToken = (lazyResult as any).nextContinuationToken">← 点击填入</span>
      </p>
      <ResultPanel :status="lazyStatus" :result="lazyResult" :error="lazyError" label="LazyListResult" />
    </ApiCard>

    <!-- 完整目录树 -->
    <ApiCard method="GET" path="/oss/object/tree" summary="获取完整目录树">
      <div class="mb-3">
        <p class="section-label">path</p>
        <input v-model="treePath" class="oss-input" placeholder="demo/" />
      </div>
      <button class="btn btn-ghost" :disabled="treeStatus === 'loading'" @click="execTree(() => ossApi.query.getTree(treePath))">
        <span v-if="treeStatus === 'loading'" class="spinner" />获取目录树
      </button>
      <ResultPanel :status="treeStatus" :result="treeResult" :error="treeError" label="ObjectTreeNode（树形）" />
    </ApiCard>

    <!-- 关键字搜索树 -->
    <ApiCard method="GET" path="/oss/object/tree/search" summary="按关键字搜索目录树">
      <div class="grid grid-cols-2 gap-3 mb-3">
        <div>
          <p class="section-label">path</p>
          <input v-model="searchPath" class="oss-input" placeholder="demo/" />
        </div>
        <div>
          <p class="section-label">keyword</p>
          <input v-model="searchKeyword" class="oss-input" placeholder="image" />
        </div>
      </div>
      <button class="btn btn-ghost" :disabled="searchStatus === 'loading'" @click="execSearch(() => ossApi.query.searchTree(searchPath, searchKeyword))">
        <span v-if="searchStatus === 'loading'" class="spinner" />搜索
      </button>
      <ResultPanel :status="searchStatus" :result="searchResult" :error="searchError" label="ObjectTreeNode（过滤后）" />
    </ApiCard>

    <!-- 文件夹树 -->
    <ApiCard method="GET" path="/oss/object/tree/folder" summary="获取文件夹树（不含文件）">
      <div class="mb-3">
        <p class="section-label">path</p>
        <input v-model="folderTreePath" class="oss-input" placeholder="demo/" />
      </div>
      <button class="btn btn-ghost" :disabled="folderTreeStatus === 'loading'" @click="execFolderTree(() => ossApi.query.getFolderTree(folderTreePath))">
        <span v-if="folderTreeStatus === 'loading'" class="spinner" />获取
      </button>
      <ResultPanel :status="folderTreeStatus" :result="folderTreeResult" :error="folderTreeError" label="ObjectTreeNode[]（仅文件夹）" />
    </ApiCard>

    <!-- 列举 Bucket -->
    <ApiCard method="GET" path="/oss/buckets" summary="列举所有 Bucket">
      <button class="btn btn-ghost" :disabled="bucketsStatus === 'loading'" @click="execBuckets(() => ossApi.query.listBuckets())">
        <span v-if="bucketsStatus === 'loading'" class="spinner" />列举 Buckets
      </button>
      <ResultPanel :status="bucketsStatus" :result="bucketsResult" :error="bucketsError" label="Bucket[]" />
    </ApiCard>

    <!-- 预览 / 下载 -->
    <div class="card mb-4">
      <div class="flex items-center gap-3 mb-4">
        <span class="method method-GET">GET</span>
        <code class="text-xs text-[var(--color-muted)]">/oss/object/{preview|download}/**</code>
        <p class="text-sm font-medium">预览 / 下载文件</p>
      </div>

      <div class="grid grid-cols-2 gap-4">
        <div>
          <p class="section-label">预览 objectName</p>
          <input v-model="previewKey" class="oss-input mb-2" placeholder="demo/example.jpg" />
          <button class="btn btn-ghost" @click="previewFile">在新标签页预览</button>
        </div>
        <div>
          <p class="section-label">下载 objectName</p>
          <input v-model="downloadKey" class="oss-input mb-2" placeholder="demo/example.txt" />
          <button class="btn btn-ghost" @click="downloadFile">触发下载</button>
        </div>
      </div>
    </div>
  </div>
</template>
