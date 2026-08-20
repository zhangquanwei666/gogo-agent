import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // 开发时前端跑在 5173，后端在 18080，靠代理转发避免跨域，
    // 后端不用加任何 CORS 配置
    // 后端每加一个新的一级路径（/order、/agent ...），这里都要跟着加一条，
    // 漏了的话请求会被 Vite 自己吞掉、返回 404 的 HTML，而不是转发给后端
    proxy: {
      '/user': {
        target: 'http://127.0.0.1:18080',
        changeOrigin: true,
      },
      '/chat': {
        target: 'http://127.0.0.1:18080',
        changeOrigin: true,
      },
    },
  },
})
