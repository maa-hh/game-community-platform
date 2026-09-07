import { setAuthTip } from './storage';

export const AUTH_REQUIRED_EVENT = 'game-community:auth-required';

/** 在非 React 请求层通知全局登录弹窗，避免通过整页跳转处理鉴权失败。 */
export function emitAuthRequired(tip = '登录已过期，请重新登录'): void {
  setAuthTip(tip);
  window.dispatchEvent(new Event(AUTH_REQUIRED_EVENT));
}
