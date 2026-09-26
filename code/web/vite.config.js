import { defineConfig } from 'vite'

// base: './' 保证所有构建产物资源引用为相对路径(需求约束)
export default defineConfig({
  base: './',
  server: {
    port: 5173,
    host: '127.0.0.1'
  },
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    sourcemap: false
  }
})
