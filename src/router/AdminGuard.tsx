import React from 'react';
import { Navigate, Outlet } from 'react-router-dom';

import { useAppSelector } from '@/store';
import { isAuthenticated } from '@/utils/storage';

function AdminGuard() {
  const { user } = useAppSelector((state) => state.auth);
  const loggedIn = isAuthenticated();

  if (!loggedIn) {
    return (
      <Navigate to="/login" replace state={{ tip: '请先登录管理员账号' }} />
    );
  }

  if (user?.type !== 1) {
    return <Navigate to="/" replace />;
  }

  return <Outlet />;
}

export default AdminGuard;
