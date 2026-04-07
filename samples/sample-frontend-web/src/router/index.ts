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
    icon: '↑',
    routes: [
      { name: 'simple-upload',    label: '单文件上传',  path: '/upload/simple' },
      { name: 'multipart-upload', label: '分片上传',    path: '/upload/multipart' },
    ],
  },
  {
    label: '文件查询',
    icon: '⊙',
    routes: [
      { name: 'query', label: '查询 / 预览 / 下载', path: '/query' },
    ],
  },
  {
    label: '文件删除',
    icon: '✕',
    routes: [
      { name: 'delete', label: '删除操作', path: '/delete' },
    ],
  },
  {
    label: '复制 / 移动',
    icon: '⇌',
    routes: [
      { name: 'copy-move', label: '复制 & 移动', path: '/copy-move' },
    ],
  },
  {
    label: '流式解压',
    icon: '⊕',
    routes: [
      { name: 'unzip', label: 'ZIP 解压', path: '/unzip' },
    ],
  },
  {
    label: '预签名 URL',
    icon: '⚿',
    routes: [
      { name: 'presign', label: '预签名 URL', path: '/presign' },
    ],
  },
  {
    label: '标签管理',
    icon: '◈',
    routes: [
      { name: 'tag', label: '对象 & Bucket 标签', path: '/tag' },
    ],
  },
  {
    label: 'Bucket 管理',
    icon: '▣',
    routes: [
      { name: 'bucket', label: 'Bucket 配置', path: '/bucket' },
    ],
  },
]

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: '/upload/simple' },
  { path: '/upload/simple',    name: 'simple-upload',    component: () => import('@/views/upload/SimpleUpload.vue') },
  { path: '/upload/multipart', name: 'multipart-upload', component: () => import('@/views/upload/MultipartUpload.vue') },
  { path: '/query',            name: 'query',            component: () => import('@/views/query/QueryView.vue') },
  { path: '/delete',           name: 'delete',           component: () => import('@/views/delete/DeleteView.vue') },
  { path: '/copy-move',        name: 'copy-move',        component: () => import('@/views/copy-move/CopyMoveView.vue') },
  { path: '/unzip',            name: 'unzip',            component: () => import('@/views/unzip/UnzipView.vue') },
  { path: '/presign',          name: 'presign',          component: () => import('@/views/presign/PresignView.vue') },
  { path: '/tag',              name: 'tag',              component: () => import('@/views/tag/TagView.vue') },
  { path: '/bucket',           name: 'bucket',           component: () => import('@/views/bucket/BucketView.vue') },
]

export const router = createRouter({
  history: createWebHashHistory(),
  routes,
})
