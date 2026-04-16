// ── API Response wrapper ─────────────────────────────────────────────
export interface R<T = unknown> {
  code: number
  msg: string
  data: T
}

// ── Object / File info ───────────────────────────────────────────────
export interface ObjectInfo {
  name: string
  uri: string
  url: string
  size: number
  ext?: string | null
  uploadTime?: string | null
}

export interface ObjectTreeNode extends ObjectInfo {
  type?: string | null
  children?: ObjectTreeNode[] | null
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
  maxKeys: number
  continuationToken?: string | null
  records: ObjectInfo[]
}

export interface BucketInfo {
  name: string
  creationDate?: string | null
}
