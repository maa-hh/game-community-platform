import React, { useEffect } from 'react';
import { Outlet } from 'react-router-dom';

import { useAuthModal } from '@/hooks/useAuthModal';
import { isAuthenticated } from '@/utils/storage';

/**
 * 鉴权守卫：未登录时在当前路由打开全局登录弹窗，不做整页跳转。
 * 以会话标记为准（对应服务端持有 HttpOnly refresh Cookie）。
 */
function AuthGuard() {
  const { openAuth } = useAuthModal();
  const authenticated = isAuthenticated();

  useEffect(() => {
    if (!authenticated) openAuth('login');
  }, [authenticated, openAuth]);

  return <Outlet />;
}

export default AuthGuard;
