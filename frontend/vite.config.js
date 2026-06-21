var _a;
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
var proxyTarget = (_a = process.env.VITE_PROXY_TARGET) !== null && _a !== void 0 ? _a : "http://127.0.0.1:8080";
export default defineConfig({
    plugins: [react()],
    server: {
        proxy: {
            "/api": {
                target: proxyTarget,
                changeOrigin: true,
                rewrite: function (path) { return path.replace(/^\/api/, ""); }
            }
        }
    }
});
