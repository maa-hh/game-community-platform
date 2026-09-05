import React from 'react';
import { Navigate, useLocation, Outlet } from 'react-router-dom';

import { isAuthenticated } from '@/utils/storage';

/**
 * 鉴权守卫：未登录时跳转官网首页，并携带提示信息
 * 以会话标记为准（对应服务端持有 HttpOnly refresh Cookie）
 */
function AuthGuard() {
  const location = useLocation();

  if (!isAuthenticated()) {
    return (
      <Navigate
        to="/"
        replace
        state={{
          from: location.pathname,
          tip: '请先登录后再访问',
        }}
      />
    );
  }

  return <Outlet />;
}

export default AuthGuard;
