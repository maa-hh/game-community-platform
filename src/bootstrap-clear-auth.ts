import { clearAuth } from '@/utils/storage';
import { resetAuthRefreshState } from '@/service/request';

/**
 * 一次性清除本地登录态（access + 会话标记 + refresh Cookie），便于测试
 */
const AUTH_RESET_FLAG = 'game_community_auth_httponly_v2_cleared';

if (
  typeof localStorage !== 'undefined' &&
  localStorage.getItem(AUTH_RESET_FLAG) !== '1'
) {
  resetAuthRefreshState();
  clearAuth();
  localStorage.setItem(AUTH_RESET_FLAG, '1');
}
