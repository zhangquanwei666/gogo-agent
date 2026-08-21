import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    // 开发时前端跑在 5173，后端在 18080，靠代理转发避免跨域，
    // 后端不用加任何 CORS 配置
    // 所有接口都在 /api 下，一条规则搞定，
    // 后端再加多少 controller 这里都不用动
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:18080',
        changeOrigin: true,
      },
    },
  },
})
