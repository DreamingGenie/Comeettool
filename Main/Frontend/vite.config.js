import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  // 개발 서버 프록시 대상. 상대 경로(/api, /uploads)로 부르는 요청이 여기로 간다.
  // 기본은 로컬 백엔드이고, 다른 서버를 쓰려면 .env에 VITE_DEV_PROXY_TARGET을 둔다.
  const env = loadEnv(mode, process.cwd(), '')
  const backend = env.VITE_DEV_PROXY_TARGET || 'http://127.0.0.1:8080'

  return {
    plugins: [vue()],
    server: {
      host: '0.0.0.0',
      port: 5173,
      proxy: {
        '/api': {
          target: backend,
          changeOrigin: true
        },
        '/uploads': {
          target: backend,
          changeOrigin: true
        },
        '/collaboration': {
          target: 'ws://127.0.0.1:3000',
          changeOrigin: true,
          ws: true
        }
      }
    },
    build: {
      target: 'es2020',
      outDir: '../dist',
      emptyOutDir: true
    }
  }
})
