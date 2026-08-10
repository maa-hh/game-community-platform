import React, {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
} from 'react';
import type { AuthMode } from '@/components/auth/constants';

interface AuthModalContextValue {
  open: boolean;
  mode: AuthMode;
  openAuth: (mode?: AuthMode) => void;
  closeAuth: () => void;
  setMode: (mode: AuthMode) => void;
}

const AuthModalContext = createContext<AuthModalContextValue | null>(null);

export function AuthModalProvider({ children }: { children: React.ReactNode }) {
  const [open, setOpen] = useState(false);
  const [mode, setMode] = useState<AuthMode>('login');

  const openAuth = useCallback((nextMode: AuthMode = 'login') => {
    setMode(nextMode === 'reset' ? 'reset' : nextMode);
    setOpen(true);
  }, []);

  const closeAuth = useCallback(() => {
    setOpen(false);
  }, []);

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
