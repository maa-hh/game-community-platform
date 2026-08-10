// API 全局配置：环境变量优先；开发留空时走当前站点（配合 craco devServer 代理）
export const BASE_URL =
  process.env.REACT_APP_BASE_URL !== undefined &&
  process.env.REACT_APP_BASE_URL !== ''
    ? process.env.REACT_APP_BASE_URL
    : process.env.NODE_ENV === 'development'
      ? ''
      : 'http://localhost:8080';

// 请求超时（毫秒）
export const TIMEOUT = 10000;

/**
 * Token 存储策略
 *
 * - accessToken（短期）：localStorage，业务请求 Header: Authorization Bearer
 * - refreshToken（长期）：HttpOnly Cookie（由服务端 Set-Cookie）
 *   · 前端永不读写 refresh 值，只开 withCredentials，浏览器自动带 Cookie
 *   · 登录态标记 AUTH_SESSION_KEY 存 localStorage（HttpOnly 前端读不到 Cookie）
 * - accessExpireAt：localStorage，用于请求前主动刷新
 *
 * Mock 说明：浏览器 JS 无法真正写入 HttpOnly，开发环境用同名 Cookie 模拟，
 * 刷新接口从 document.cookie 读取（等价于服务端读 Cookie 头）。
 */
export const ACCESS_TOKEN_KEY = 'game_community_access_token';
export const ACCESS_EXPIRE_AT_KEY = 'game_community_access_expire_at';
export const AUTH_SESSION_KEY = 'game_community_auth_session';

/** refresh Cookie 名（生产由后端 Set-Cookie；HttpOnly） */
export const REFRESH_COOKIE_NAME = 'game_community_refresh_token';

/** 提前多少毫秒刷新 access（避免临界过期） */
export const ACCESS_REFRESH_BUFFER_MS = 5 * 1000;

/** 业务码：access 失效 */
export const CODE_ACCESS_EXPIRED = 40101;
/** 业务码：refresh 失效 */
export const CODE_REFRESH_EXPIRED = 40102;

/** 与后端 ApiErrorCodes 对齐的通用业务码 */
export const CODE_BAD_REQUEST = 400;
export const CODE_UNAUTHORIZED = 401;
export const CODE_FORBIDDEN = 403;
export const CODE_NOT_FOUND = 404;
export const CODE_CONFLICT = 409;
export const CODE_TOO_MANY_REQUESTS = 429;
export const CODE_INTERNAL_ERROR = 500;

// 是否启用 mock（开发环境默认开启，可用环境变量关闭）
export const ENABLE_MOCK =
  process.env.REACT_APP_ENABLE_MOCK !== 'false' &&
  process.env.NODE_ENV === 'development';
