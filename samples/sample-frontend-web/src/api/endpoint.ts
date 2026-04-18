const OSS_HTTP_PREFIX = '/api/oss'
const runtimeBackendStorageKey = 'oss-demo-runtime-backend-base-url'

function normalizeBackendBaseUrl(rawValue?: string | null): string {
    const trimmedValue = (rawValue ?? '').trim()
    if (!trimmedValue) {
        return ''
    }
    return trimmedValue.replace(/\/+$/, '')
}

function appendOssHttpPrefix(backendBaseUrl: string): string {
    if (backendBaseUrl.endsWith(OSS_HTTP_PREFIX)) {
        return backendBaseUrl
    }
    if (backendBaseUrl.endsWith('/api')) {
        return `${backendBaseUrl}/oss`
    }
    return `${backendBaseUrl}${OSS_HTTP_PREFIX}`
}

function canUseBrowserStorage(): boolean {
    return typeof window !== 'undefined' && typeof window.localStorage !== 'undefined'
}

function readRuntimeBackendBaseUrl(): string {
    if (!canUseBrowserStorage()) {
        return ''
    }
    try {
        return normalizeBackendBaseUrl(window.localStorage.getItem(runtimeBackendStorageKey))
    } catch (error) {
        // GitHub Pages 的前端演示必须允许“无法访问 localStorage”时继续工作，
        // 因此这里降级为空字符串，回退到仓库默认配置或同源路径，而不是让整个应用初始化失败。
        console.warn('读取前端演示后端地址失败，将回退到默认配置', error)
        return ''
    }
}

export function resolveConfiguredBackendBaseUrl(): string {
    return normalizeBackendBaseUrl(import.meta.env.VITE_API_BASE_URL)
}

export function hasRuntimeBackendOverride(): boolean {
    return readRuntimeBackendBaseUrl() !== ''
}

export function resolveEffectiveBackendBaseUrl(): string {
    const runtimeBackendBaseUrl = readRuntimeBackendBaseUrl()
    if (runtimeBackendBaseUrl) {
        return runtimeBackendBaseUrl
    }
    return resolveConfiguredBackendBaseUrl()
}

export function resolveEffectiveOssApiBaseUrl(): string {
    const effectiveBackendBaseUrl = resolveEffectiveBackendBaseUrl()
    if (!effectiveBackendBaseUrl) {
        return OSS_HTTP_PREFIX
    }
    return appendOssHttpPrefix(effectiveBackendBaseUrl)
}

export function persistRuntimeBackendBaseUrl(rawValue: string): string {
    const normalizedBackendBaseUrl = normalizeBackendBaseUrl(rawValue)
    if (!canUseBrowserStorage()) {
        return normalizedBackendBaseUrl
    }
    try {
        if (normalizedBackendBaseUrl) {
            window.localStorage.setItem(runtimeBackendStorageKey, normalizedBackendBaseUrl)
        } else {
            window.localStorage.removeItem(runtimeBackendStorageKey)
        }
    } catch (error) {
        console.warn('保存前端演示后端地址失败，本次仅在当前会话内生效', error)
    }
    return normalizedBackendBaseUrl
}

export function clearRuntimeBackendBaseUrl(): void {
    if (!canUseBrowserStorage()) {
        return
    }
    try {
        window.localStorage.removeItem(runtimeBackendStorageKey)
    } catch (error) {
        console.warn('清理前端演示后端地址失败', error)
    }
}

