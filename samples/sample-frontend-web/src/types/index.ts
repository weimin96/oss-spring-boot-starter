// ── API Response wrapper ─────────────────────────────────────────────
export interface R<T = unknown> {
  code: number
  msg: string
  data: T
}

// ── Object / File info ───────────────────────────────────────────────
export interface ObjectInfo {
  objectName: string
  url: string
  size: number
  contentType: string
  lastModified: string
  etag?: string
}

export interface ObjectTreeNode {
  name: string
  path: string
  isDir: boolean
  children?: ObjectTreeNode[]
  size?: number
  lastModified?: string
}

// ── Multipart upload ─────────────────────────────────────────────────
export interface ChunkTarget {
  partNumber: number
  etag: string
}

export interface ChunkPart {
  partNumber: number
  etag: string
  size: number
}

// ── Unzip ────────────────────────────────────────────────────────────
export interface UnzipResult {
  successCount: number
  failCount: number
  files: string[]
  errors?: string[]
}

// ── Lazy list ────────────────────────────────────────────────────────
export interface LazyListResult {
  objects: ObjectInfo[]
  nextContinuationToken?: string
  isTruncated: boolean
}
