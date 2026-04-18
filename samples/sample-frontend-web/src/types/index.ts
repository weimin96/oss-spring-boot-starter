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
    succeeded: ObjectInfo[]
    failed: string[]
    targetPath?: string | null
    succeededCount: number
    failedCount: number
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

export type BucketCannedAcl = 'private' | 'public-read' | 'public-read-write' | 'authenticated-read'

export interface BucketAccessInfo {
    bucketName: string
    acl: string
    supported: boolean
    message?: string | null
}

export interface BucketDetailInfo {
    name: string
    creationDate?: string | null
    access?: string | null
    totalSize: number
    totalObjectCount: number
    tags: Record<string, string>
}

export interface BucketRewindResult {
    bucketName: string
    targetTime?: string | null
    scannedObjectCount: number
    restoredObjectCount: number
    deletedObjectCount: number
    skippedObjectCount: number
}

export interface LifecycleRuleInfo {
    id?: string | null
    status?: string | null
    prefix?: string | null
    expirationDays?: number | null
    expirationDate?: string | null
    expiredObjectDeleteMarker?: boolean | null
}

export interface CorsRuleInfo {
    id?: string | null
    allowedOrigins: string[]
    allowedMethods: string[]
    allowedHeaders: string[]
    exposeHeaders: string[]
    maxAgeSeconds?: number | null
}
