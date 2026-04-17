import { createRouter, createWebHashHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

export interface NavGroup {
  label: string
  icon: string
  routes: { name: string; label: string; path: string }[]
}

export const navGroups: NavGroup[] = [
  {
    label: '文件上传',
    icon: '⏫',
    routes: [
      { name: 'upload', label: '单文件上传', path: '/upload' },
      { name: 'create-folder', label: '创建文件夹', path: '/upload/folder' },
      { name: 'multipart-upload', label: '分片上传', path: '/upload/multipart' },
    ],
  },
  {
    label: '文件查询',
    icon: '🔍',
    routes: [
      { name: 'connect', label: '连接测试', path: '/query/connect' },
      { name: 'get-object', label: '文件详情', path: '/query/get' },
      { name: 'exists', label: '文件存在检查', path: '/query/exists' },
      { name: 'list', label: '列举对象', path: '/query/list' },
      { name: 'list-next', label: '列举下一层级', path: '/query/next' },
      { name: 'lazy-list', label: '懒加载分页', path: '/query/lazy' },
      { name: 'tree', label: '完整目录树', path: '/query/tree' },
      { name: 'search-tree', label: '关键字搜索树', path: '/query/search' },
      { name: 'folder-tree', label: '文件夹树', path: '/query/folder-tree' },
      { name: 'list-buckets', label: '列举 Bucket', path: '/query/buckets' },
      { name: 'preview', label: '预览文件', path: '/query/preview' },
      { name: 'download', label: '下载文件', path: '/query/download' },
      { name: 'range-download', label: '分片下载', path: '/query/range' },
    ],
  },
  {
    label: '文件删除',
    icon: '×',
    routes: [
      { name: 'delete-object', label: '单个删除', path: '/delete/object' },
      { name: 'delete-objects', label: '批量删除', path: '/delete/objects' },
      { name: 'delete-folder', label: '文件夹删除', path: '/delete/folder' },
    ],
  },
  {
    label: '复制 / 移动',
    icon: '→',
    routes: [
      { name: 'copy', label: '复制文件', path: '/copy-move/copy' },
      { name: 'move', label: '移动文件', path: '/copy-move/move' },
    ],
  },
  {
    label: '流式解压',
    icon: '▤',
    routes: [
      { name: 'unzip', label: 'ZIP 解压', path: '/unzip' },
      { name: 'cross-bucket-unzip', label: '跨 Bucket 解压', path: '/unzip/cross' },
      { name: 'filter-unzip', label: '过滤解压', path: '/unzip/filter' },
    ],
  },
  {
    label: '预签名 URL',
    icon: '⌗',
    routes: [
      { name: 'presign-get', label: '下载预签名', path: '/presign/get' },
      { name: 'presign-put', label: '上传预签名', path: '/presign/put' },
    ],
  },
  {
    label: '标签管理',
    icon: '○',
    routes: [
      { name: 'object-tag', label: '对象标签', path: '/tag/object' },
      { name: 'bucket-tag', label: 'Bucket 标签', path: '/tag/bucket' },
    ],
  },
  {
    label: 'Bucket 管理',
    icon: '▣',
    routes: [
      { name: 'create-bucket', label: '创建 Bucket', path: '/bucket/create' },
      { name: 'bucket-detail', label: 'Bucket 详情', path: '/bucket/detail' },
      { name: 'bucket-access', label: 'Bucket ACL', path: '/bucket/access' },
      { name: 'versioning', label: '版本控制', path: '/bucket/versioning' },
      { name: 'bucket-rewind', label: 'Bucket 时间回滚', path: '/bucket/rewind' },
      { name: 'lifecycle', label: '生命周期', path: '/bucket/lifecycle' },
      { name: 'cors', label: 'CORS 配置', path: '/bucket/cors' },
      { name: 'policy', label: '访问策略', path: '/bucket/policy' },
      { name: 'security', label: '安全配置', path: '/bucket/security' },
    ],
  },
]

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/upload' },

  { path: '/upload', name: 'upload', component: () => import('@/views/upload/UploadView.vue') },
  { path: '/upload/folder', name: 'create-folder', component: () => import('@/views/upload/CreateFolderView.vue') },
  { path: '/upload/multipart', name: 'multipart-upload', component: () => import('@/views/upload/MultipartUpload.vue') },

  { path: '/query/connect', name: 'connect', component: () => import('@/views/query/ConnectView.vue') },
  { path: '/query/get', name: 'get-object', component: () => import('@/views/query/GetObjectView.vue') },
  { path: '/query/exists', name: 'exists', component: () => import('@/views/query/ExistsView.vue') },
  { path: '/query/list', name: 'list', component: () => import('@/views/query/ListView.vue') },
  { path: '/query/next', name: 'list-next', component: () => import('@/views/query/ListNextView.vue') },
  { path: '/query/lazy', name: 'lazy-list', component: () => import('@/views/query/LazyListView.vue') },
  { path: '/query/tree', name: 'tree', component: () => import('@/views/query/TreeView.vue') },
  { path: '/query/search', name: 'search-tree', component: () => import('@/views/query/SearchTreeView.vue') },
  { path: '/query/folder-tree', name: 'folder-tree', component: () => import('@/views/query/FolderTreeView.vue') },
  { path: '/query/buckets', name: 'list-buckets', component: () => import('@/views/query/ListBucketsView.vue') },
  { path: '/query/preview', name: 'preview', component: () => import('@/views/query/PreviewView.vue') },
  { path: '/query/download', name: 'download', component: () => import('@/views/query/DownloadView.vue') },
  { path: '/query/range', name: 'range-download', component: () => import('@/views/query/RangeDownloadView.vue') },

  { path: '/delete/object', name: 'delete-object', component: () => import('@/views/delete/DeleteObjectView.vue') },
  { path: '/delete/objects', name: 'delete-objects', component: () => import('@/views/delete/DeleteObjectsView.vue') },
  { path: '/delete/folder', name: 'delete-folder', component: () => import('@/views/delete/DeleteFolderView.vue') },

  { path: '/copy-move/copy', name: 'copy', component: () => import('@/views/copy-move/CopyView.vue') },
  { path: '/copy-move/move', name: 'move', component: () => import('@/views/copy-move/MoveView.vue') },

  { path: '/unzip', name: 'unzip', component: () => import('@/views/unzip/UnzipView.vue') },
  { path: '/unzip/cross', name: 'cross-bucket-unzip', component: () => import('@/views/unzip/CrossBucketUnzipView.vue') },
  { path: '/unzip/filter', name: 'filter-unzip', component: () => import('@/views/unzip/FilterUnzipView.vue') },

  { path: '/presign/get', name: 'presign-get', component: () => import('@/views/presign/PresignGetView.vue') },
  { path: '/presign/put', name: 'presign-put', component: () => import('@/views/presign/PresignPutView.vue') },

  { path: '/tag/object', name: 'object-tag', component: () => import('@/views/tag/ObjectTagView.vue') },
  { path: '/tag/bucket', name: 'bucket-tag', component: () => import('@/views/tag/BucketTagView.vue') },

  { path: '/bucket/create', name: 'create-bucket', component: () => import('@/views/bucket/CreateBucketView.vue') },
  { path: '/bucket/detail', name: 'bucket-detail', component: () => import('@/views/bucket/BucketDetailView.vue') },
  { path: '/bucket/access', name: 'bucket-access', component: () => import('@/views/bucket/BucketAccessView.vue') },
  { path: '/bucket/versioning', name: 'versioning', component: () => import('@/views/bucket/VersioningView.vue') },
  { path: '/bucket/rewind', name: 'bucket-rewind', component: () => import('@/views/bucket/BucketRewindView.vue') },
  { path: '/bucket/lifecycle', name: 'lifecycle', component: () => import('@/views/bucket/LifecycleView.vue') },
  { path: '/bucket/cors', name: 'cors', component: () => import('@/views/bucket/CorsView.vue') },
  { path: '/bucket/policy', name: 'policy', component: () => import('@/views/bucket/PolicyView.vue') },
  { path: '/bucket/security', name: 'security', component: () => import('@/views/bucket/SecurityView.vue') },
]

export const router = createRouter({
  history: createWebHashHistory(),
  routes,
})
