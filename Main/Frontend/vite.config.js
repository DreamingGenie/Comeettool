import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://70.12.247.162:8080',
        changeOrigin: true
      },
      '/uploads': {
        target: 'http://70.12.247.162:8080',
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
    outDir: '../dist',
    emptyOutDir: true
  }
})
