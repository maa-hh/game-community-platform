import { useCallback } from 'react';

import { useAuthModal } from '@/hooks/useAuthModal';
import { useAppSelector } from '@/store';

/** 需要登录才继续：未登录则打开登录弹窗，返回 false */
export function useRequireLogin() {
  const { user } = useAppSelector((s) => s.auth);
  const { openAuth } = useAuthModal();

  const requireLogin = useCallback(() => {
    if (user?.accountId) return true;
    openAuth('login');
    return false;
  }, [user?.accountId, openAuth]);

  return {
    user,
    isLoggedIn: Boolean(user?.accountId),
    requireLogin,
    openAuth,
  };
}
