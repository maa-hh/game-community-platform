const fs = require('fs');
const http = require('http');
const https = require('https');
const httpProxy = require('http-proxy');
const path = require('path');
const { URL } = require('url');

const PORT = Number(process.env.PORT || 3000);
const BACKEND_URL = new URL(process.env.BACKEND_URL || 'http://127.0.0.1:8080');
const BUILD_DIR = path.resolve(__dirname, '..', 'build');
const INDEX_FILE = path.join(BUILD_DIR, 'index.html');
const DANMAKU_WS_PREFIX = '/danmaku/ws/';

const websocketProxy = httpProxy.createProxyServer({
  target: BACKEND_URL.origin,
  changeOrigin: true,
  ws: true,
});

websocketProxy.on('error', (error, request, socket) => {
  console.error(`WebSocket 代理失败: ${request.url}`, error.message);
  if (socket && !socket.destroyed) {
    socket.destroy();
  }
});

const API_CONTEXTS = [
  '/user/',
  '/social/',
  '/article/',
  '/category/',
  '/file/',
  '/share/',
  '/notification/',
  '/shop/',
  '/api/',
  '/hot-article/',
  '/search/',
  '/ai/',
  '/audit/',
  '/report/',
  '/steam/',
  // /game/:appId is also a client-side route; isApiRequest only proxies
  // non-HTML requests so browser navigation still falls back to index.html.
  '/game/',
  '/danmaku/',
];

const MIME_TYPES = {
  '.css': 'text/css; charset=utf-8',
  '.gif': 'image/gif',
  '.html': 'text/html; charset=utf-8',
  '.ico': 'image/x-icon',
  '.jpeg': 'image/jpeg',
  '.jpg': 'image/jpeg',
  '.js': 'application/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.webp': 'image/webp',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
};

function isApiRequest(requestUrl, headers) {
  const pathname = requestUrl.pathname;
  if (pathname === '/api/shop' || pathname.startsWith('/api/shop/')) {
    return false;
  }

  const acceptsHtml = String(headers.accept || '').includes('text/html');
  if (acceptsHtml) return false;

  return API_CONTEXTS.some(
    (context) => pathname === context.slice(0, -1) || pathname.startsWith(context),
  );
}

function proxyToBackend(req, res, requestUrl) {
  const transport = BACKEND_URL.protocol === 'https:' ? https : http;
  const target = new URL(requestUrl.pathname + requestUrl.search, BACKEND_URL);
  const headers = { ...req.headers, host: BACKEND_URL.host };

  const proxyReq = transport.request(
    target,
    {
      method: req.method,
      headers,
      timeout: 0,
    },
    (proxyRes) => {
      res.writeHead(proxyRes.statusCode || 502, proxyRes.headers);
      proxyRes.pipe(res);
    },
  );

  proxyReq.on('timeout', () => proxyReq.destroy(new Error('backend request timeout')));
  proxyReq.on('error', (error) => {
    if (res.headersSent) {
      res.destroy(error);
      return;
    }
    res.writeHead(502, { 'content-type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify({ code: 502, message: '后端网关不可用' }));
  });

  req.pipe(proxyReq);
}

function resolveStaticFile(requestPath) {
  let decodedPath;
  try {
    decodedPath = decodeURIComponent(requestPath);
  } catch {
    return null;
  }

  const relativePath = decodedPath.replace(/^\/+/, '');
  const filePath = path.resolve(BUILD_DIR, relativePath);
  if (filePath !== BUILD_DIR && !filePath.startsWith(`${BUILD_DIR}${path.sep}`)) {
    return null;
  }
  return filePath;
}

function serveFile(req, res, filePath) {
  fs.stat(filePath, (error, stats) => {
    if (error || !stats.isFile()) {
      res.writeHead(404, { 'content-type': 'text/plain; charset=utf-8' });
      res.end('Not Found');
      return;
    }

    res.writeHead(200, {
      'cache-control': path.basename(filePath) === 'index.html'
        ? 'no-cache'
        : 'public, max-age=31536000, immutable',
      'content-type': MIME_TYPES[path.extname(filePath).toLowerCase()] || 'application/octet-stream',
      'content-length': stats.size,
    });
    if (req.method === 'HEAD') {
      res.end();
      return;
    }
    fs.createReadStream(filePath).pipe(res);
  });
}

if (!fs.existsSync(INDEX_FILE)) {
  console.error(`build 目录不存在，请先执行 npm run build: ${BUILD_DIR}`);
  process.exit(1);
}

const server = http.createServer((req, res) => {
  const requestUrl = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`);

  if (isApiRequest(requestUrl, req.headers)) {
    proxyToBackend(req, res, requestUrl);
    return;
  }

  const staticFile = resolveStaticFile(requestUrl.pathname);
  if (staticFile && fs.existsSync(staticFile) && fs.statSync(staticFile).isFile()) {
    serveFile(req, res, staticFile);
    return;
  }

  serveFile(req, res, INDEX_FILE);
});

// 生产静态服务器默认只处理 HTTP；弹幕连接是 WebSocket upgrade，必须
// 单独转发到网关，否则浏览器会一直停在 CONNECTING 状态。
server.on('upgrade', (req, socket, head) => {
  let requestUrl;
  try {
    requestUrl = new URL(req.url || '/', `http://${req.headers.host || 'localhost'}`);
  } catch {
    socket.destroy();
    return;
  }

  if (!requestUrl.pathname.startsWith(DANMAKU_WS_PREFIX)) {
    socket.destroy();
    return;
  }

  websocketProxy.ws(req, socket, head);
});

server.timeout = 0;
server.requestTimeout = 0;
server.keepAliveTimeout = 65000;
server.listen(PORT, '0.0.0.0', () => {
  console.log(`生产前端: http://0.0.0.0:${PORT}`);
  console.log(`API 代理: ${BACKEND_URL.origin}`);
});
