import { defineConfig } from 'vitest/config';
import vue from '@vitejs/plugin-vue';

/**
 * Vite 构建配置。
 * 开发模式:前端 5173 端口,API 与 WebSocket 通过 proxy 转发到后端 5100,
 *           前端代码里统一用相对路径(/api、/hubs),开发与生产无差别。
 * 生产模式:npm run build 产物拷到 jar 同级的 wwwroot/(或打包进 resources/static),由后端同源托管。
 */
export default defineConfig({
  plugins: [vue()],
  test: {
    environment: 'jsdom',
  },
  server: {
    port: 5173,
    proxy: {
      // REST API 转发
      '/api': { target: 'http://localhost:5100', changeOrigin: true },
      // 实时通道(WebSocket)转发
      '/hubs': { target: 'http://localhost:5100', changeOrigin: true, ws: true },
    },
  },
  build: {
    chunkSizeWarningLimit: 1500, // three/echarts 体积较大,放宽告警阈值
  },
});
