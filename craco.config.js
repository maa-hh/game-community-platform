// CRACO 配置：https://craco.js.org/
// 用于覆盖 Create React App（react-scripts）的内置 webpack/babel/jest 配置，无需 eject
const path = require('path');
const CracoLessPlugin = require('craco-less');

const API_PROXY_CONTEXT = [
  '/user/',
  '/social/',
  '/article/',
  '/category/',
  '/file/',
  '/share/',
  '/notification/',
  '/shop/',
  '/api/shop/',
  '/api/',
  '/hot-article/',
  '/search/',
  '/ai/',
  '/audit/',
  '/report/',
  '/game/',
  '/steam/',
  '/danmaku/',
];

// 页面路由与 API 共用 /game、/search、/shop 等前缀。
// 只有非 HTML 请求才转发到网关，避免刷新页面时把 React 路由转成后端 API 请求。
const isApiProxyRequest = (pathname, req) => {
  const accept = req.headers.accept || '';
  if (accept.includes('text/html')) {
    return false;
  }
  return API_PROXY_CONTEXT.some(
    (context) => pathname === context.slice(0, -1) || pathname.startsWith(context),
  );
};

module.exports = {
  devServer: {
    proxy: [{
      context: isApiProxyRequest,
      target: 'http://localhost:8080',
      changeOrigin: true,
      ws: true,
    }],
  },
  // webpack 配置块：会被深度合并到 CRA 默认 webpack 配置上
  webpack: {
    // 模块解析别名，等价于 webpack resolve.alias
    alias: {
      // 把 @ 映射到 src 目录，import xxx from '@/xxx' 会被解析到 src/xxx
      '@': path.resolve(__dirname, 'src'),
    },
    // 如需进一步自定义完整 webpack 配置，可启用 configure：
    // configure: (webpackConfig, { env, paths }) => {
    //   // 修改 webpackConfig 后 return
    //   return webpackConfig;
    // },
  },

  // 插件列表：每个插件对应一个 CRA 内置 webpack 规则的覆盖
  plugins: [
    {
      // 启用 LESS 支持：让 .less 文件能被 import 并编译为 CSS
      plugin: CracoLessPlugin,
      options: {
        // 传递给 less-loader 的选项
        lessOptions: {
          // 开启 less 变量覆盖与 calc 等特性
          javascriptEnabled: true,
          // 在所有 .less 文件顶部自动注入这些变量/mixin（按需启用）
          // modifyVars: {},
        },
      },
    },
  ],
};
