import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ mode }) => {
  const target = loadEnv(mode, '.', 'VITE_').VITE_CAREER_DEV_PROXY_TARGET;
  if (target && !/^http:\/\/(localhost|127\.0\.0\.1):\d+\/?$/.test(target)) throw new Error('Career development proxy requires a local HTTP target.');
  return {
    plugins: [react()],
    server: {
      port: 5173,
      ...(target ? { proxy: { '/api': { target, changeOrigin: true, configure(proxy) { proxy.on('proxyReq', request => request.removeHeader('origin')); } } } } : {}),
    },
  };
});
