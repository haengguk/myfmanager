var __assign = (this && this.__assign) || function () {
    __assign = Object.assign || function(t) {
        for (var s, i = 1, n = arguments.length; i < n; i++) {
            s = arguments[i];
            for (var p in s) if (Object.prototype.hasOwnProperty.call(s, p))
                t[p] = s[p];
        }
        return t;
    };
    return __assign.apply(this, arguments);
};
import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
export default defineConfig(function (_a) {
    var mode = _a.mode;
    var target = loadEnv(mode, '.', 'VITE_').VITE_CAREER_DEV_PROXY_TARGET;
    if (target && !/^http:\/\/(localhost|127\.0\.0\.1):\d+\/?$/.test(target))
        throw new Error('Career development proxy requires a local HTTP target.');
    return {
        plugins: [react()],
        server: __assign({ port: 5173 }, (target ? { proxy: { '/api': { target: target, changeOrigin: true, configure: function (proxy) { proxy.on('proxyReq', function (request) { return request.removeHeader('origin'); }); } } } } : {})),
    };
});
