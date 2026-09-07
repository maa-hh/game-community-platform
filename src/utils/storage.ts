/**
 * 登录态本地存储（双 token 方案）
 *
 * ## 两个 token 各存哪、谁读写
 *
 * | 数据 | 存储 | 前端能否读到 | 用途 |
 * |------|------|--------------|------|
 * | access（短期） | localStorage | ✅ | 业务请求 Header: Authorization Bearer |
 * | refresh（长期） | HttpOnly Cookie | ❌ | 不经过 JS；浏览器 withCredentials 自动带给服务端 |
 * | 会话标记 | localStorage AUTH_SESSION_KEY | ✅ | Cookie 读不到，用「1」表示仍持有 refresh 会话 |
 *
 * ## 前端拿不到 refresh 值，双 token 怎么工作？
 *
 * 不是靠「前端读 refresh」，而是浏览器代发 Cookie + 服务端校验：
 *
 * 1. 登录：JSON 只返回 access → 本文件 setAccessAuth 写入 localStorage；
 *    refresh 由服务端 Set-Cookie（Mock 见 mock/index.ts setRefreshCookie）。
 * 2. 日常请求：前端从 localStorage 取 access 塞 Header；浏览器同时自动附带 Cookie。
 * 3. access 过期：request.ts 发 POST /user/auth/refresh（body 为空，withCredentials: true），
 *    服务端从 Cookie 读 refresh，返回新 access → 再调 setAccessAuth 更新。
 *
 * refresh 永不 localStorage.setItem，避免 XSS 偷长期凭证。
 * 认证信息只使用当前版本的存储键。
 *
 * @see service/request.ts doRefreshToken
 * @see service/config.ts Token 存储策略说明
 */
import {
  ACCESS_TOKEN_KEY,
  ACCESS_EXPIRE_AT_KEY,
  AUTH_SESSION_KEY,
  REFRESH_COOKIE_NAME,
} from '@/service/config';
import type { IUserInfo } from '@/service/types';
import { normalizeUserInfo } from '@/service/types';

/** 跨标签页监听用户资料变化时使用的 localStorage key。 */
export const USER_INFO_STORAGE_KEY = 'game_community_user';
const AUTH_TIP_KEY = 'game_community_auth_tip';

// ---------- access token（短期，localStorage） ----------

export function getAccessToken(): string {
  return localStorage.getItem(ACCESS_TOKEN_KEY) || '';
}

export function setAccessToken(token: string): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, token);
}

export function removeAccessToken(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
}

// ---------- 登录会话标记（HttpOnly Cookie 前端读不到，用此判断是否登录） ----------

export function setAuthSession(): void {
  localStorage.setItem(AUTH_SESSION_KEY, '1');
}

export function clearAuthSession(): void {
  localStorage.removeItem(AUTH_SESSION_KEY);
}

export function hasAuthSession(): boolean {
  return localStorage.getItem(AUTH_SESSION_KEY) === '1';
}

// ---------- access 过期时间 ----------

export function getAccessExpireAt(): number {
  const raw = localStorage.getItem(ACCESS_EXPIRE_AT_KEY);
  return raw ? Number(raw) || 0 : 0;
}

export function setAccessExpireAt(expireAt: number): void {
  localStorage.setItem(ACCESS_EXPIRE_AT_KEY, String(expireAt));
}

export function removeAccessExpireAt(): void {
  localStorage.removeItem(ACCESS_EXPIRE_AT_KEY);
}

/** access 是否已过期（含缓冲窗口外） */
export function isAccessTokenExpired(bufferMs = 0): boolean {
  const expireAt = getAccessExpireAt();
  if (!expireAt) return !getAccessToken();
  return Date.now() >= expireAt - bufferMs;
}

/**
 * 是否已登录
 * 以会话标记为准（对应服务端仍持有 HttpOnly refresh Cookie）
 */
export function isAuthenticated(): boolean {
  return hasAuthSession();
}

/**
 * 写入 access（refresh 只由服务端/Mock 写入 Cookie，前端不落盘）
 */
export function setAccessAuth(payload: {
  accessToken: string;
  accessExpiresIn: number;
}): void {
  setAccessToken(payload.accessToken);
  setAccessExpireAt(Date.now() + payload.accessExpiresIn * 1000);
  setAuthSession();
}

/**
 * 登出时清除前端可见的 refresh Cookie（Mock 用）
 * 生产环境应调 /user/auth/logout，由服务端清除 HttpOnly Cookie
 */
export function clearRefreshCookie(): void {
  document.cookie = `${REFRESH_COOKIE_NAME}=; Path=/; Max-Age=0; SameSite=Strict`;
}

// ---------- 用户信息 ----------

export function getUserInfo(): IUserInfo | null {
  const raw = localStorage.getItem(USER_INFO_STORAGE_KEY);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as IUserInfo;
    if (parsed.accountId == null) return null;
    return normalizeUserInfo(parsed);
  } catch {
    return null;
  }
}

export function setUserInfo(user: IUserInfo): void {
  localStorage.setItem(USER_INFO_STORAGE_KEY, JSON.stringify(user));
}

export function removeUserInfo(): void {
  localStorage.removeItem(USER_INFO_STORAGE_KEY);
}

// ---------- 登录过期提示（跨整页跳转） ----------

export function setAuthTip(tip: string): void {
  sessionStorage.setItem(AUTH_TIP_KEY, tip);
}

export function consumeAuthTip(): string {
  const tip = sessionStorage.getItem(AUTH_TIP_KEY) || '';
  if (tip) sessionStorage.removeItem(AUTH_TIP_KEY);
  return tip;
}

// ---------- 清除登录态 ----------

export function clearAuth(): void {
  removeAccessToken();
  removeAccessExpireAt();
  clearAuthSession();
  removeUserInfo();
  clearRefreshCookie();
}
