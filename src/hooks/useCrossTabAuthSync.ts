import { useEffect } from 'react';

import { resetAuthRefreshState } from '@/service/request';
import { USER_INFO_STORAGE_KEY } from '@/utils/storage';

/**
 * 同源标签页共享 localStorage/Cookie 登录态。
 *
 * 另一个标签页登录、退出或更新资料时，当前标签页的 Redux 内存和页面
 * 实例不会自动跟着变化。监听用户资料 key，并整页重新初始化，确保：
 * - Redux auth.user 与共享 access token 属于同一个账号；
 * - MainLayout 的页面缓存按新账号重新建立；
 * - SSE、通知和各页面请求不会继续使用旧账号的内存状态。
 */
export function useCrossTabAuthSync(): void {
  useEffect(() => {
    const handleStorage = (event: StorageEvent) => {
      if (event.key !== USER_INFO_STORAGE_KEY) return;

      // 取消当前标签页尚未完成的 refresh，避免旧会话的结果覆盖新会话。
      resetAuthRefreshState();
      window.location.reload();
    };

    window.addEventListener('storage', handleStorage);
    return () => window.removeEventListener('storage', handleStorage);
  }, []);
}
