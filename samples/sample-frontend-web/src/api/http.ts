import axios, { type AxiosRequestConfig } from 'axios'
import type { R } from '@/types'

const http = axios.create({
  baseURL:'/api/oss',
  timeout: 60_000,
})

http.interceptors.response.use(
  (res) => {
    const body = res.data as R<unknown>
    if (body.code !== undefined && body.code !== 200 && body.code !== 0) {
      return Promise.reject(new Error(body.msg ?? `Error ${body.code}`))
    }
    return res
  },
  (err) => Promise.reject(err),
)

export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const res = await http(config)
  const body = res.data as R<T>
  return body.data as T
}

export async function requestRaw(config: AxiosRequestConfig) {
  return http(config)
}

export default http
