import axios, {type AxiosRequestConfig} from 'axios'
import type {R} from '@/types'

const http = axios.create({
    baseURL: '/api/oss',
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
    (err) => {
        // 把后端统一响应里的 msg 透传给前端页面，
        // 这样参数校验失败时能直接展示服务端返回的领域错误消息。
        if (axios.isAxiosError(err)) {
            const responseData = err.response?.data
            if (responseData instanceof Blob) {
                return responseData.text().then((text) => {
                    const message = text && text.trim() ? text : err.message
                    return Promise.reject(new Error(message))
                })
            }
            const body = err.response?.data as Partial<R<unknown>> | undefined
            if (body?.msg) {
                return Promise.reject(new Error(body.msg))
            }
        }
        return Promise.reject(err)
    },
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
