import {defineConfig, loadEnv} from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'
import {resolve} from 'path'

function normalizePagesBasePath(rawBasePath?: string): string {
    const trimmedBasePath = (rawBasePath ?? '/').trim()
    if (!trimmedBasePath) {
        return '/'
    }
    const normalizedWithLeadingSlash = trimmedBasePath.startsWith('/') ? trimmedBasePath : `/${trimmedBasePath}`
    return normalizedWithLeadingSlash.endsWith('/') ? normalizedWithLeadingSlash : `${normalizedWithLeadingSlash}/`
}

export default defineConfig(({mode}) => {
    const environment = loadEnv(mode, __dirname, '')
    const backendBaseUrl = environment.VITE_API_BASE_URL || 'http://127.0.0.1:8080'

    return {
        base: normalizePagesBasePath(environment.VITE_BASE_PATH),
        plugins: [
            vue(),
            tailwindcss(),
        ],
        resolve: {
            alias: {
                '@': resolve(__dirname, 'src'),
            },
        },
        server: {
            port: 5173,
            proxy: {
                '/api': {
                    target: backendBaseUrl,
                    changeOrigin: true
                },
            },
        },
    }
})
