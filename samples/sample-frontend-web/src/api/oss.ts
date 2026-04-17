import { request, requestRaw } from './http'
import type {
  ObjectInfo,
  ObjectTreeNode,
  BucketAccessInfo,
  BucketCannedAcl,
  BucketDetailInfo,
  BucketInfo,
  CorsRuleInfo,
  ChunkTarget,
  BucketRewindResult,
  LifecycleRuleInfo,
  UnzipResult,
  LazyListResult,
} from '@/types'

interface MultipartInitRequest {
  filename: string
  path: string
  signal?: AbortSignal
}

interface MultipartChunkUploadRequest {
  file: File
  chunkNumber: number
  filename: string
  path: string
  guid: string
  uploadId: string
  signal?: AbortSignal
}

interface MultipartMergeRequest {
  filename: string
  path: string
  uploadId: string
  guid: string
  chunkTargetList: ChunkTarget[]
  signal?: AbortSignal
}

function buildMultipartChunkForm(payload: MultipartChunkUploadRequest): FormData {
  if (payload.file.size <= 0) {
    throw new Error(`分片 ${payload.chunkNumber} 内容为空，已阻止无效上传请求`)
  }

  const form = new FormData()
  form.append('file', payload.file, payload.file.name)
  form.append('chunkNumber', String(payload.chunkNumber))
  form.append('filename', payload.filename)
  form.append('path', payload.path)
  form.append('guid', payload.guid)
  form.append('uploadId', payload.uploadId)
  return form
}

export const ossApi = {

  connect: {
    test: () => request<boolean>({ method: 'GET', url: '/connect' }),
  },

  // ── 普通上传 ──────────────────────────────────────────────────────
  upload: {
    putObject(
      file: File,
      path: string,
      filename?: string,
      onProgress?: (pct: number) => void,
    ): Promise<ObjectInfo> {
      const form = new FormData()
      form.append('file', file)
      form.append('path', path)
      if (filename) form.append('filename', filename)
      return request<ObjectInfo>({
        method: 'POST',
        url: '/object',
        data: form,
        onUploadProgress: (e: { loaded: number; total?: number }) => {
          if (e.total) onProgress?.(Math.round((e.loaded / e.total) * 100))
        },
      })
    },

    createFolder: (path: string) =>
      request<ObjectInfo>({ method: 'POST', url: '/folder', params: { path } }),
  },

  // ── 分片上传 ──────────────────────────────────────────────────────
  multipart: {
    /**
     * POST /multipart/init
     * 初始化分片任务，参数通过 query 传递，便于与后端 `ChunkTask` 绑定保持一致。
     */
    init: ({ filename, path, signal }: MultipartInitRequest) =>
      request<string>({ method: 'POST', url: '/multipart/init', params: { filename, path }, signal }),

    /**
     * POST /multipart/chunk
     * 分片字段全部通过 FormData 传递。
     * 这里显式要求 `File` 而不是裸 `Blob`，是为了让浏览器稳定携带文件名与边界信息，
     * 同时在发送前拦截空分片，避免后端拿到空 `MultipartFile` 后进入错误合并流程。
     */
    uploadChunk: (payload: MultipartChunkUploadRequest) => {
      return request<ChunkTarget>({
        method: 'POST',
        url: '/multipart/chunk',
        data: buildMultipartChunkForm(payload),
        signal: payload.signal,
      })
    },

    /**
     * POST /multipart/merge
     * 分片合并通过 JSON body 传递，与后端 `ChunkMerge` 对象结构保持一致。
     */
    merge: ({ filename, path, uploadId, guid, chunkTargetList, signal }: MultipartMergeRequest) =>
      request<ObjectInfo>({
        method: 'POST',
        url: '/multipart/merge',
        signal,
        data: { filename, path, uploadId, guid, chunkTargetList },
      }),

    /** GET /multipart/parts */
    listParts: (objectName: string, uploadId: string) =>
      request<unknown[]>({ method: 'GET', url: '/multipart/parts', params: { objectName, uploadId } }),
  },

  // ── 文件查询 ──────────────────────────────────────────────────────
  query: {
    /**
     * GET /object
     * 注意：对象不存在或后端识别为存储侧异常时，服务端会返回成功包裹下的 null，
     * 前端必须把该结果当成“没有数据”，不能误判为 void 成功。
     */
    getObject: (objectName: string) =>
      request<ObjectInfo | null>({ method: 'GET', url: '/object', params: { objectName } }),

    exists: (objectName: string) =>
      request<boolean>({ method: 'GET', url: '/object/exists', params: { objectName } }),

    listObjects: (path: string) =>
      request<ObjectInfo[]>({ method: 'GET', url: '/object/list', params: { path } }),

    listNextLevel: (path: string) =>
      request<ObjectTreeNode[]>({ method: 'GET', url: '/object/list/next-level', params: { path } }),

    lazyList: (path: string, maxKeys = 100, continuationToken?: string) =>
      request<LazyListResult>({
        method: 'GET',
        url: '/object/list/lazy',
        params: { path, maxKeys, continuationToken },
      }),

    /**
     * GET /object/tree
     * 注意：若路径下无对象，后端返回 null，前端应按空结果处理
     */
    getTree: (path: string) =>
      request<ObjectTreeNode | null>({ method: 'GET', url: '/object/tree', params: { path } }),

    /**
     * GET /object/tree/search
     * 注意：若无匹配，后端返回 null，前端应按空结果处理
     */
    searchTree: (path: string, keyword: string) =>
      request<ObjectTreeNode | null>({ method: 'GET', url: '/object/tree/search', params: { path, keyword } }),

    getFolderTree: (path: string) =>
      request<ObjectTreeNode[]>({ method: 'GET', url: '/object/tree/folder', params: { path } }),

    listBuckets: () =>
      request<BucketInfo[]>({ method: 'GET', url: '/buckets' }),

    /** GET /object/preview/** — inline 预览，返回原始响应 */
    preview: (objectName: string) =>
      requestRaw({ method: 'GET', url: `/object/preview/${objectName}`, responseType: 'blob' }),

    /** GET /object/download/** — attachment 下载，返回原始响应 */
    download: (objectName: string) =>
      requestRaw({ method: 'GET', url: `/object/download/${objectName}`, responseType: 'blob' }),

    /**
     * GET /object/download/** — 分片下载（Range 请求）
     * @param objectName 对象 key
     * @param start      字节起始位置（含）
     * @param end        字节结束位置（含），不传则到文件末尾
     */
    downloadRange: (objectName: string, start: number, end?: number) =>
      requestRaw({
        method: 'GET',
        url: `/object/download/${objectName}`,
        responseType: 'blob',
        headers: { Range: end !== undefined ? `bytes=${start}-${end}` : `bytes=${start}-` },
      }),
  },

  // ── 文件删除 ──────────────────────────────────────────────────────
  delete: {
    deleteObject: (objectName: string) =>
      request<void>({ method: 'DELETE', url: '/object', params: { objectName } }),

    deleteObjects: (objectNames: string[]) =>
      request<void>({ method: 'DELETE', url: '/objects', data: objectNames }),

    deleteFolder: (path: string) =>
      request<void>({ method: 'DELETE', url: '/folder', params: { path } }),
  },

  // ── 复制 / 移动 ───────────────────────────────────────────────────
  move: {
    /** POST /object/copy — 返回 void（后端 R.success("复制成功")，data 为 null） */
    copy: (sourceKey: string, destKey: string) =>
      request<void>({ method: 'POST', url: '/object/copy', params: { sourceKey, destKey } }),

    /** POST /object/move — 返回 void */
    move: (sourceKey: string, destPath: string) =>
      request<void>({ method: 'POST', url: '/object/move', params: { sourceKey, destPath } }),
  },

  // ── 解压 ──────────────────────────────────────────────────────────
  unzip: {
    unzip: (zipObjectKey: string, targetPath: string) =>
      request<UnzipResult>({ method: 'POST', url: '/unzip', params: { zipObjectKey, targetPath } }),

    crossBucket: (
      sourceBucket: string, zipObjectKey: string,
      targetBucket: string, targetPath: string,
    ) =>
      request<UnzipResult>({
        method: 'POST', url: '/unzip/cross-bucket',
        params: { sourceBucket, zipObjectKey, targetBucket, targetPath },
      }),

    withFilter: (zipObjectKey: string, entryPrefix: string, targetPath: string) =>
      request<UnzipResult>({
        method: 'POST', url: '/unzip/filter',
        params: { zipObjectKey, entryPrefix, targetPath },
      }),
  },

  // ── 预签名 URL ────────────────────────────────────────────────────
  presign: {
    getUrl: (objectName: string, expirationSeconds = 3600) =>
      request<string>({
        method: 'GET', url: '/presign/get',
        params: { objectName, expirationSeconds },
      }),

    putUrl: (objectName: string, contentType = 'application/octet-stream', expirationSeconds = 3600) =>
      request<string>({
        method: 'GET', url: '/presign/put',
        params: { objectName, contentType, expirationSeconds },
      }),
  },

  // ── 对象标签 ──────────────────────────────────────────────────────
  objectTags: {
    get: (objectName: string) =>
      request<Record<string, string>>({ method: 'GET', url: '/object/tags', params: { objectName } }),

    /** PUT — 覆盖，返回 void（data 为 null） */
    set: (objectName: string, tags: Record<string, string>) =>
      request<void>({ method: 'PUT', url: '/object/tags', params: { objectName }, data: tags }),

    /** PATCH — 合并，返回 void */
    merge: (objectName: string, tags: Record<string, string>) =>
      request<void>({ method: 'PATCH', url: '/object/tags', params: { objectName }, data: tags }),

    delete: (objectName: string) =>
      request<void>({ method: 'DELETE', url: '/object/tags', params: { objectName } }),
  },

  // ── Bucket 管理 ───────────────────────────────────────────────────
  bucket: {
    /** POST /bucket — 返回 void */
    create: (bucketName: string) =>
      request<void>({ method: 'POST', url: '/bucket', params: { bucketName } }),

    getDetail: (bucketName: string) =>
      request<BucketDetailInfo>({ method: 'GET', url: `/buckets/${bucketName}` }),

    getAccess: (bucketName: string) =>
      request<BucketAccessInfo>({ method: 'GET', url: `/buckets/${bucketName}/access` }),

    setAccess: (bucketName: string, acl: BucketCannedAcl) =>
      request<BucketAccessInfo>({
        method: 'PUT',
        url: `/buckets/${bucketName}/access`,
        params: { acl },
      }),

    rewind: (bucketName: string, targetTime: string) =>
      request<BucketRewindResult>({
        method: 'POST',
        url: `/buckets/${bucketName}/rewind`,
        params: { targetTime },
      }),

    getVersioning: () => request<string>({ method: 'GET', url: '/bucket/versioning' }),
    enableVersioning: () => request<void>({ method: 'PUT', url: '/bucket/versioning/enable' }),
    suspendVersioning: () => request<void>({ method: 'PUT', url: '/bucket/versioning/suspend' }),

    getLifecycle: () => request<LifecycleRuleInfo[]>({ method: 'GET', url: '/bucket/lifecycle' }),
    deleteLifecycle: () => request<void>({ method: 'DELETE', url: '/bucket/lifecycle' }),
    addExpiration: (ruleId: string, prefix: string, expirationDays: number) =>
      request<void>({
        method: 'POST', url: '/bucket/lifecycle/expiration',
        params: { ruleId, prefix, expirationDays },
      }),

    getCors: () => request<CorsRuleInfo[]>({ method: 'GET', url: '/bucket/cors' }),
    allowAllCors: () => request<void>({ method: 'PUT', url: '/bucket/cors/allow-all' }),
    deleteCors: () => request<void>({ method: 'DELETE', url: '/bucket/cors' }),

    getPolicy: () => request<string>({ method: 'GET', url: '/bucket/policy' }),
    putPolicy: (policyJson: string) =>
      request<void>({
        method: 'PUT', url: '/bucket/policy',
        data: policyJson,
        headers: { 'Content-Type': 'application/json' },
      }),
    deletePolicy: () => request<void>({ method: 'DELETE', url: '/bucket/policy' }),

    enableEncryption: () => request<void>({ method: 'PUT', url: '/bucket/encryption/enable' }),
    blockPublicAccess: () => request<void>({ method: 'PUT', url: '/bucket/public-access/block' }),

    // Bucket Tags
    getTags: () => request<Record<string, string>>({ method: 'GET', url: '/bucket/tags' }),
    /** PUT — 返回 void */
    setTags: (tags: Record<string, string>) =>
      request<void>({ method: 'PUT', url: '/bucket/tags', data: tags }),
    deleteTags: () => request<void>({ method: 'DELETE', url: '/bucket/tags' }),
  },
}
