/**
 * OSS API — mirrors OssController endpoints.
 *
 * Base prefix is configured via VITE_API_BASE_URL (default: /api/oss).
 * All functions return the unwrapped `data` from R<T>.
 *
 * Chunk bean fields (actual Java):
 *   chunkNumber, filename, path, guid, file(MultipartFile), uploadId
 * ChunkTask bean fields:
 *   filename, path
 * ChunkMerge bean fields:
 *   filename, path, uploadId, guid, chunkTargetList: [{partNumber, etag}]
 */
import { request, requestRaw } from './http'
import type {
  ObjectInfo,
  ObjectTreeNode,
  ChunkTarget,
  UnzipResult,
  LazyListResult,
} from '@/types'

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
     * ChunkTask: { filename, path } — 以 query params 传递（@Validated 绑定）
     */
    init: (filename: string, path: string) =>
      request<string>({ method: 'POST', url: '/multipart/init', params: { filename, path } }),

    /**
     * POST /multipart/chunk
     * Chunk 所有字段通过 FormData 传递（后端用 @Validated 绑定 multipart 表单）:
     *   file(binary), chunkNumber, filename, path, guid, uploadId
     *
     * 注意：blob 作为文件字段时必须指定 filename，否则部分浏览器不携带 Content-Disposition
     */
    uploadChunk: (
      blob: Blob,
      chunkNumber: number,
      filename: string,
      path: string,
      guid: string,
      uploadId: string,
    ) => {
      const form = new FormData()
      // file 字段必须附带 filename，与后端 MultipartFile 字段匹配
      form.append('file', blob, filename)
      form.append('chunkNumber', String(chunkNumber))
      form.append('filename', filename)
      form.append('path', path)
      form.append('guid', guid)
      form.append('uploadId', uploadId)
      return request<ChunkTarget>({
        method: 'POST',
        url: '/multipart/chunk',
        data: form,
        // 不设置 Content-Type，让浏览器自动生成 multipart/form-data; boundary=...
        headers: { 'Content-Type': undefined },
      })
    },

    /**
     * POST /multipart/merge
     * ChunkMerge 通过 JSON body 传递（@RequestBody）
     */
    merge: (
      filename: string,
      path: string,
      uploadId: string,
      guid: string,
      chunkTargetList: ChunkTarget[],
    ) =>
      request<ObjectInfo>({
        method: 'POST',
        url: '/multipart/merge',
        data: { filename, path, uploadId, guid, chunkTargetList },
      }),

    /** GET /multipart/parts */
    listParts: (objectName: string, uploadId: string) =>
      request<unknown[]>({ method: 'GET', url: '/multipart/parts', params: { objectName, uploadId } }),
  },

  // ── 文件查询 ──────────────────────────────────────────────────────
  query: {
    getObject: (objectName: string) =>
      request<ObjectInfo>({ method: 'GET', url: '/object', params: { objectName } }),

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

    /**
     * GET /buckets
     * 注意：直接返回 SDK Bucket 对象列表，可能触发序列化问题（HttpMessageConversionException）
     * 后端 workaround：在 application.yml 配置 jackson 忽略未知字段
     */
    listBuckets: () =>
      request<unknown[]>({ method: 'GET', url: '/buckets' }),

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

    getVersioning: () => request<string>({ method: 'GET', url: '/bucket/versioning' }),
    enableVersioning: () => request<void>({ method: 'PUT', url: '/bucket/versioning/enable' }),
    suspendVersioning: () => request<void>({ method: 'PUT', url: '/bucket/versioning/suspend' }),

    getLifecycle: () => request<unknown[]>({ method: 'GET', url: '/bucket/lifecycle' }),
    deleteLifecycle: () => request<void>({ method: 'DELETE', url: '/bucket/lifecycle' }),
    addExpiration: (ruleId: string, prefix: string, expirationDays: number) =>
      request<void>({
        method: 'POST', url: '/bucket/lifecycle/expiration',
        params: { ruleId, prefix, expirationDays },
      }),

    getCors: () => request<unknown[]>({ method: 'GET', url: '/bucket/cors' }),
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
