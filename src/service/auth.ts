import hyRequest from './request';
import type {
  IDataType,
  IAuthResult,
  ILoginParams,
  IRegisterParams,
  IRegisterResult,
  IResetPasswordParams,
  ISendCodeParams,
  ISendCodeResult,
  IRefreshResult,
} from './types';

/** 邮箱密码登录 → JSON 返回 access；refresh 由 Cookie（HttpOnly）下发 */
export function loginByEmail(data: ILoginParams) {
  return hyRequest.post<IDataType<IAuthResult>>({
    url: '/user/auth/login',
    data,
    skipAuth: true,
  });
}

/** 发送邮箱验证码 */
export function sendVerifyCode(data: ISendCodeParams) {
  return hyRequest.post<IDataType<ISendCodeResult>>({
    url: '/user/auth/send-code',
    data,
    skipAuth: true,
  });
}

/** 邮箱注册（仅创建账号，不发 token） */
export function registerByEmail(data: IRegisterParams) {
  return hyRequest.post<IDataType<IRegisterResult>>({
    url: '/user/auth/register',
    data,
    skipAuth: true,
  });
}

/** 找回密码（验证码 + 新密码） */
export function resetPasswordByEmail(data: IResetPasswordParams) {
  return hyRequest.post<IDataType<null>>({
    url: '/user/auth/reset-password',
    data,
    skipAuth: true,
  });
}

/** 主动登出：调后端作废 token 并清本地 */
export function logoutApi() {
  return hyRequest.post<IDataType<null>>({
    url: '/user/auth/logout',
    data: {},
    skipAuth: true,
  });
}

/**
 * 刷新 access：不传 refreshToken，依赖浏览器自动携带 HttpOnly Cookie
 * （withCredentials: true）
 */
export function refreshAccessToken() {
  return hyRequest.post<IDataType<IRefreshResult>>({
    url: '/user/auth/refresh',
    data: {},
    skipAuth: true,
  });
}
