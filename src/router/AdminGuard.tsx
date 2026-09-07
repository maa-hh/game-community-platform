import React, { useEffect } from 'react';
import { Navigate, Outlet } from 'react-router-dom';

import { useAuthModal } from '@/hooks/useAuthModal';
import { useAppSelector } from '@/store';
import { isAuthenticated } from '@/utils/storage';

function AdminGuard() {
  const { user } = useAppSelector((state) => state.auth);
  const { openAuth } = useAuthModal();
  const loggedIn = isAuthenticated();

  useEffect(() => {
    if (!loggedIn) openAuth('login');
  }, [loggedIn, openAuth]);

  if (!loggedIn) {
    return <Outlet />;
  }

  if (user?.type !== 1) {
    return <Navigate to="/community" replace />;
  }

  return <Outlet />;
}

export default AdminGuard;
