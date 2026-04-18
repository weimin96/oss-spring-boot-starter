import {ref} from 'vue'

export type Status = 'idle' | 'loading' | 'success' | 'error'

export function useResult<T = unknown>() {
    const status = ref<Status>('idle')
    const result = ref<T | null>(null)
    const error = ref<string | null>(null)

    async function execute(fn: () => Promise<T>) {
        status.value = 'loading'
        error.value = null
        result.value = null
        try {
            result.value = await fn()
            status.value = 'success'
        } catch (e: unknown) {
            error.value = e instanceof Error ? e.message : String(e)
            status.value = 'error'
        }
    }

    function reset() {
        status.value = 'idle'
        result.value = null
        error.value = null
    }

    return {status, result, error, execute, reset}
}
