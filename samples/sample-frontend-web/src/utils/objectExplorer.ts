import type { ObjectInfo, ObjectTreeNode } from '@/types'

export type ObjectEntryKind = 'file' | 'folder'

export interface ObjectListItem {
  key: string
  name: string
  uri: string
  kind: ObjectEntryKind
  size?: number
  ext?: string | null
  uploadTime?: string | null
  url?: string
}

export interface ObjectTreeEntry extends ObjectListItem {
  children: ObjectTreeEntry[]
}

function normalizeUri(uri?: string | null): string {
  return (uri ?? '').trim().replace(/^\/+/, '')
}

function normalizePathPrefix(path: string): string {
  const normalized = normalizeUri(path)
  if (!normalized) return ''
  return normalized.endsWith('/') ? normalized : `${normalized}/`
}

function buildDisplayName(entry: Pick<ObjectInfo, 'name' | 'uri'>): string {
  if (entry.name?.trim()) return entry.name.trim()
  const uri = normalizeUri(entry.uri)
  if (!uri) return '(根路径)'
  const trimmed = uri.endsWith('/') ? uri.slice(0, -1) : uri
  const segments = trimmed.split('/').filter(Boolean)
  return segments[segments.length - 1] ?? trimmed
}

function inferKind(entry: Partial<ObjectInfo> & Partial<ObjectTreeNode>): ObjectEntryKind {
  if (entry.type === 'folder') return 'folder'
  if (entry.type === 'file') return 'file'
  const uri = normalizeUri(entry.uri)
  return uri.endsWith('/') ? 'folder' : 'file'
}

function toListItem(entry: ObjectInfo | ObjectTreeNode): ObjectListItem {
  const uri = normalizeUri(entry.uri)
  return {
    key: entry.url || uri || entry.name,
    name: buildDisplayName(entry),
    uri,
    kind: inferKind(entry),
    size: entry.size,
    ext: entry.ext,
    uploadTime: entry.uploadTime,
    url: entry.url,
  }
}

function compareEntries(a: ObjectListItem, b: ObjectListItem): number {
  if (a.kind !== b.kind) return a.kind === 'folder' ? -1 : 1
  return a.uri.localeCompare(b.uri, 'zh-CN')
}

function sortTreeEntries(nodes: ObjectTreeEntry[]): ObjectTreeEntry[] {
  return nodes
    .slice()
    .sort(compareEntries)
    .map((node) => ({
      ...node,
      children: sortTreeEntries(node.children),
    }))
}

function buildTreeNode(entry: ObjectInfo | ObjectTreeNode): ObjectTreeEntry {
  const base = toListItem(entry)
  const children = Array.isArray((entry as ObjectTreeNode).children)
    ? normalizeTreeNodes((entry as ObjectTreeNode).children ?? [])
    : []

  return {
    ...base,
    children,
  }
}

function createFolderEntry(name: string, uri: string): ObjectTreeEntry {
  return {
    key: `folder:${uri}`,
    name,
    uri,
    kind: 'folder',
    size: 0,
    ext: null,
    uploadTime: null,
    url: '',
    children: [],
  }
}

function buildUriFromSegments(prefix: string, segments: string[], kind: ObjectEntryKind): string {
  const relative = segments.join('/')
  const combined = prefix ? `${prefix}${relative}` : relative
  return kind === 'folder' ? `${combined}/` : combined
}

export function formatObjectSize(size?: number | null): string {
  if (typeof size !== 'number' || Number.isNaN(size)) return '-'
  if (size <= 0) return '0 B'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  if (size < 1024 * 1024 * 1024) return `${(size / 1024 / 1024).toFixed(2)} MB`
  return `${(size / 1024 / 1024 / 1024).toFixed(2)} GB`
}

export function formatObjectTime(uploadTime?: string | null): string {
  return uploadTime?.trim() || '-'
}

export function formatObjectExt(ext?: string | null, kind?: ObjectEntryKind): string {
  if (kind === 'folder') return '目录'
  return ext?.trim() || '-'
}

export function normalizeObjectListItems(items: Array<ObjectInfo | ObjectTreeNode>): ObjectListItem[] {
  return items.map(toListItem).sort(compareEntries)
}

export function normalizeTreeNodes(nodes: ObjectTreeNode[]): ObjectTreeEntry[] {
  return sortTreeEntries(nodes.map(buildTreeNode))
}

export function normalizeTreeRoot(node: ObjectTreeNode | null | undefined): ObjectTreeEntry[] {
  if (!node) return []
  return normalizeTreeNodes([node])
}

/**
 * 懒加载与平铺列表接口只返回对象数组。
 * 为了复用统一树形组件，这里按对象路径构造只存在于前端视图层的临时目录树。
 */
export function buildTreeFromObjectInfos(items: ObjectInfo[], rootPath: string): ObjectTreeEntry[] {
  const rootNodes: ObjectTreeEntry[] = []
  const prefix = normalizePathPrefix(rootPath)

  items.forEach((item) => {
    const base = toListItem(item)
    const fullUri = normalizeUri(base.uri)
    const relativeUri = prefix && fullUri.startsWith(prefix) ? fullUri.slice(prefix.length) : fullUri
    const normalizedRelative = relativeUri.endsWith('/') ? relativeUri.slice(0, -1) : relativeUri
    const segments = normalizedRelative.split('/').filter(Boolean)

    if (segments.length === 0) {
      rootNodes.push({ ...base, children: [] })
      return
    }

    let currentChildren = rootNodes

    segments.forEach((segment, index) => {
      const isLeaf = index === segments.length - 1
      const kind = isLeaf ? base.kind : 'folder'
      const nodeUri = buildUriFromSegments(prefix, segments.slice(0, index + 1), kind)
      let currentNode = currentChildren.find((node) => node.uri === nodeUri)

      if (!currentNode) {
        currentNode = isLeaf
          ? { ...base, children: [] }
          : createFolderEntry(segment, nodeUri)
        currentChildren.push(currentNode)
      }

      currentChildren = currentNode.children
    })
  })

  return sortTreeEntries(rootNodes)
}
