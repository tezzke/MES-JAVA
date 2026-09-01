import { defineConfig } from 'vitest/config';
import vue from '@vitejs/plugin-vue';

/**
 * Vite 构建配置。
 * 开发模式:前端 5173 端口,API 与 WebSocket 通过 proxy 转发到后端 5100,
 *           前端代码里统一用相对路径(/api、/hubs),开发与生产无差别。
 * 生产模式:npm run build 产物拷到 jar 同级的 wwwroot/(或打包进 resources/static),由后端同源托管。
 *
 *
 */
export default defineConfig({
  plugins: [vue()],
  test: {
    environment: 'jsdom',
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      // REST API 转发。内网 IP 访问时浏览器 Origin 是 http://<局域网>:5173，
      // 这里改写成本机来源，避免后端把登录 POST 误判成非法跨域（表现为 403 权限不足）。
      '/api': {
        target: 'http://localhost:5100',
        changeOrigin: true,
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq) => {
            proxyReq.setHeader('Origin', 'http://localhost:5173');
          });
        },
      },
      // 实时通道(WebSocket)转发
      '/hubs': { target: 'http://localhost:5100', changeOrigin: true, ws: true },
    },
  },
  build: {
    chunkSizeWarningLimit: 1500, // three/echarts 体积较大,放宽告警阈值
  },
});
