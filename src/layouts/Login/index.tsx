import React from 'react';
import { Outlet } from 'react-router-dom';

/** 登录落地页布局：不套内容限宽盒，便于全屏视频（顶栏在 RootLayout） */
function LoginLayout() {
  return (
    <div className="login-layout">
      <Outlet />
    </div>
  );
}

export default LoginLayout;
