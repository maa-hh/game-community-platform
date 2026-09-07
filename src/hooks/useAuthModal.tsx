import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';
import type { AuthMode } from '@/components/auth/constants';
import { useAppDispatch } from '@/store';
import { logout } from '@/store/modules/auth';
import { AUTH_REQUIRED_EVENT } from '@/utils/authEvents';

interface AuthModalContextValue {
  open: boolean;
  mode: AuthMode;
  openAuth: (mode?: AuthMode) => void;
  closeAuth: () => void;
  setMode: (mode: AuthMode) => void;
}

const AuthModalContext = createContext<AuthModalContextValue | null>(null);

export function AuthModalProvider({ children }: { children: React.ReactNode }) {
  const dispatch = useAppDispatch();
  const [open, setOpen] = useState(false);
  const [mode, setMode] = useState<AuthMode>('login');

  const openAuth = useCallback((nextMode: AuthMode = 'login') => {
    setMode(nextMode === 'reset' ? 'reset' : nextMode);
    setOpen(true);
  }, []);

  const closeAuth = useCallback(() => {
    setOpen(false);
  }, []);

  useEffect(() => {
    const handleAuthRequired = () => {
      dispatch(logout());
      openAuth('login');
    };

    window.addEventListener(AUTH_REQUIRED_EVENT, handleAuthRequired);
    return () =>
      window.removeEventListener(AUTH_REQUIRED_EVENT, handleAuthRequired);
  }, [dispatch, openAuth]);

  const value = useMemo(
    () => ({ open, mode, openAuth, closeAuth, setMode }),
    [open, mode, openAuth, closeAuth],
  );

  return (
    <AuthModalContext.Provider value={value}>
      {children}
    </AuthModalContext.Provider>
  );
}

export function useAuthModal() {
  const ctx = useContext(AuthModalContext);
  if (!ctx) {
    throw new Error('useAuthModal must be used within AuthModalProvider');
  }
  return ctx;
}
