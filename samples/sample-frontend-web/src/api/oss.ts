/**
 * OSS API — mirrors OssController endpoints.
 *
 * Base prefix is configured via VITE_API_BASE_URL (default: /api/oss).
 * All functions return the unwrapped `data` from R<T>.
 */
import { request, requestRaw } from './http'
import type {
  ObjectInfo,
  ObjectTreeNode,
  ChunkTarget,
  ChunkPart,
  UnzipResult,
  LazyListResult,
} from '@/types'

// ── 连接测试 ──────────────────────────────────────────────────────────
export const ossApi = {

  connect: {
    test: () => request<boolean>({ method: 'GET', url: '/connect' }),
  },

  // ── 普通上传 ──────────────────────────────────────────────────────
  upload: {
    /**
     * POST /object — 单文件上传
     * @param file      File 对象
     * @param path      OSS 存放路径（如 images/）
     * @param filename  自定义文件名，为空则使用原始文件名
     * @param onProgress 上传进度回调 0~100
     */
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
        onUploadProgress: (e) => {
          if (e.total) onProgress?.(Math.round((e.loaded / e.total) * 100))
        },
      })
    },

    /** POST /folder — 创建文件夹 */
    createFolder: (path: string) =>
      request<ObjectInfo>({ method: 'POST', url: '/folder', params: { path } }),
  },

  // ── 分片上传 ──────────────────────────────────────────────────────
  multipart: {
    /**
     * POST /multipart/init — 初始化分片任务，返回 uploadId
     * ChunkTask fields: filename, path, size, chunkSize, chunkNum
     */
    init: (params: {
      filename: string
      path: string
      size: number
      chunkSize: number
      chunkNum: number
    }) =>
      request<string>({ method: 'POST', url: '/multipart/init', params }),

    /**
     * POST /multipart/chunk — 上传单个分片
     * Chunk fields: file, uploadId, objectName, partNumber
     */
    uploadChunk: (
      file: Blob,
      uploadId: string,
      objectName: string,
      partNumber: number,
    ) => {
      const form = new FormData()
      form.append('file', file)
      form.append('uploadId', uploadId)
      form.append('objectName', objectName)
      form.append('partNumber', String(partNumber))
      return request<ChunkTarget>({ method: 'POST', url: '/multipart/chunk', data: form })
    },

    /** POST /multipart/merge — 合并分片 */
    merge: (params: { uploadId: string; objectName: string; filename: string }) =>
      request<ObjectInfo>({ method: 'POST', url: '/multipart/merge', params }),

    /** GET /multipart/parts — 查询已上传分片列表 */
    listParts: (objectName: string, uploadId: string) =>
      request<ChunkPart[]>({ method: 'GET', url: '/multipart/parts', params: { objectName, uploadId } }),
  },

  // ── 文件查询 ──────────────────────────────────────────────────────
  query: {
    /** GET /object — 获取文件元数据 */
    getObject: (objectName: string) =>
      request<ObjectInfo>({ method: 'GET', url: '/object', params: { objectName } }),

    /** GET /object/exists — 检查文件是否存在 */
    exists: (objectName: string) =>
      request<boolean>({ method: 'GET', url: '/object/exists', params: { objectName } }),

    /** GET /object/list — 列举路径下所有对象 */
    listObjects: (path: string) =>
      request<ObjectInfo[]>({ method: 'GET', url: '/object/list', params: { path } }),

    /** GET /object/list/next-level — 列举下一层级 */
    listNextLevel: (path: string) =>
      request<ObjectTreeNode[]>({ method: 'GET', url: '/object/list/next-level', params: { path } }),

    /** GET /object/list/lazy — 分页懒加载列表 */
    lazyList: (path: string, maxKeys = 100, continuationToken?: string) =>
      request<LazyListResult>({
        method: 'GET',
        url: '/object/list/lazy',
        params: { path, maxKeys, continuationToken },
      }),

    /** GET /object/tree — 完整目录树 */
    getTree: (path: string) =>
      request<ObjectTreeNode>({ method: 'GET', url: '/object/tree', params: { path } }),

    /** GET /object/tree/search — 按关键字搜索目录树 */
    searchTree: (path: string, keyword: string) =>
      request<ObjectTreeNode>({ method: 'GET', url: '/object/tree/search', params: { path, keyword } }),

    /** GET /object/tree/folder — 仅文件夹节点的目录树 */
    getFolderTree: (path: string) =>
      request<ObjectTreeNode[]>({ method: 'GET', url: '/object/tree/folder', params: { path } }),

    /** GET /buckets — 列举所有 Bucket */
    listBuckets: () =>
      request<unknown[]>({ method: 'GET', url: '/buckets' }),

    /** GET /object/preview/** — 预览文件（返回 Blob） */
    preview: (objectName: string) =>
      requestRaw({ method: 'GET', url: `/object/preview/${objectName}`, responseType: 'blob' }),

    /** GET /object/download/** — 下载文件（返回 Blob） */
    download: (objectName: string) =>
      requestRaw({ method: 'GET', url: `/object/download/${objectName}`, responseType: 'blob' }),
  },

  // ── 文件删除 ──────────────────────────────────────────────────────
  delete: {
    /** DELETE /object */
    deleteObject: (objectName: string) =>
      request<void>({ method: 'DELETE', url: '/object', params: { objectName } }),

    /** DELETE /objects — 批量删除 */
    deleteObjects: (objectNames: string[]) =>
      request<void>({ method: 'DELETE', url: '/objects', data: objectNames }),

    /** DELETE /folder — 递归删除文件夹 */
    deleteFolder: (path: string) =>
      request<void>({ method: 'DELETE', url: '/folder', params: { path } }),
  },

  // ── 复制 / 移动 ───────────────────────────────────────────────────
  move: {
    /** POST /object/copy */
    copy: (sourceKey: string, destKey: string) =>
      request<void>({ method: 'POST', url: '/object/copy', params: { sourceKey, destKey } }),

    /** POST /object/move */
    move: (sourceKey: string, destPath: string) =>
      request<void>({ method: 'POST', url: '/object/move', params: { sourceKey, destPath } }),
  },

  // ── 解压 ──────────────────────────────────────────────────────────
  unzip: {
    /** POST /unzip */
    unzip: (zipObjectKey: string, targetPath: string) =>
      request<UnzipResult>({ method: 'POST', url: '/unzip', params: { zipObjectKey, targetPath } }),

    /** POST /unzip/cross-bucket */
    crossBucket: (
      sourceBucket: string, zipObjectKey: string,
      targetBucket: string, targetPath: string,
    ) =>
      request<UnzipResult>({
        method: 'POST', url: '/unzip/cross-bucket',
        params: { sourceBucket, zipObjectKey, targetBucket, targetPath },
      }),

    /** POST /unzip/filter */
    withFilter: (zipObjectKey: string, entryPrefix: string, targetPath: string) =>
      request<UnzipResult>({
        method: 'POST', url: '/unzip/filter',
        params: { zipObjectKey, entryPrefix, targetPath },
      }),
  },

  // ── 预签名 URL ────────────────────────────────────────────────────
  presign: {
    /** GET /presign/get — 生成下载预签名 URL */
    getUrl: (objectName: string, expirationSeconds = 3600) =>
      request<string>({
        method: 'GET', url: '/presign/get',
        params: { objectName, expirationSeconds },
      }),

    /** GET /presign/put — 生成上传预签名 URL */
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

    set: (objectName: string, tags: Record<string, string>) =>
      request<void>({ method: 'PUT', url: '/object/tags', params: { objectName }, data: tags }),

    merge: (objectName: string, tags: Record<string, string>) =>
      request<void>({ method: 'PATCH', url: '/object/tags', params: { objectName }, data: tags }),

    delete: (objectName: string) =>
      request<void>({ method: 'DELETE', url: '/object/tags', params: { objectName } }),
  },

  // ── Bucket 管理 ───────────────────────────────────────────────────
  bucket: {
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
      request<void>({ method: 'PUT', url: '/bucket/policy', data: policyJson,
        headers: { 'Content-Type': 'application/json' } }),
    deletePolicy: () => request<void>({ method: 'DELETE', url: '/bucket/policy' }),

    enableEncryption: () => request<void>({ method: 'PUT', url: '/bucket/encryption/enable' }),
    blockPublicAccess: () => request<void>({ method: 'PUT', url: '/bucket/public-access/block' }),

    // Bucket Tags
    getTags: () => request<Record<string, string>>({ method: 'GET', url: '/bucket/tags' }),
    setTags: (tags: Record<string, string>) =>
      request<void>({ method: 'PUT', url: '/bucket/tags', data: tags }),
    deleteTags: () => request<void>({ method: 'DELETE', url: '/bucket/tags' }),
  },
}
