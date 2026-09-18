import { clearAuth } from '@/utils/storage';
import { resetAuthRefreshState } from '@/service/request';

/**
 * 仅在开发环境，或显式开启 REACT_APP_RESET_AUTH_ON_BOOT=true 时，
 * 一次性清除本地登录态（access + 会话标记 + refresh Cookie），便于测试。
 */
const AUTH_RESET_FLAG = 'game_community_auth_httponly_v2_cleared';
const shouldResetAuth =
  process.env.NODE_ENV === 'development' ||
  process.env.REACT_APP_RESET_AUTH_ON_BOOT === 'true';

if (
  shouldResetAuth &&
  typeof localStorage !== 'undefined' &&
  localStorage.getItem(AUTH_RESET_FLAG) !== '1'
) {
  resetAuthRefreshState();
  clearAuth();
  localStorage.setItem(AUTH_RESET_FLAG, '1');
}
