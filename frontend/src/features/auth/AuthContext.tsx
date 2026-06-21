import { createContext, useContext, useEffect, useMemo, useState } from "react";
import { tokenStore } from "../../api/client";
import { LoginVO, UserVO, userApi } from "../../api/user";

type AuthContextValue = {
  user: UserVO | null;
  loading: boolean;
  isAuthenticated: boolean;
  refreshMe: () => Promise<void>;
  applyLogin: (login: LoginVO) => Promise<void>;
  logout: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<UserVO | null>(null);
  const [loading, setLoading] = useState(true);

  async function refreshMe() {
    if (!tokenStore.get()) {
      try {
        const refresh = await userApi.refreshToken();
        tokenStore.set(refresh.accessToken);
      } catch {
        tokenStore.clear();
        setUser(null);
        setLoading(false);
        return;
      }
    }
    try {
      const me = await userApi.me();
      setUser(me);
    } catch {
      tokenStore.clear();
      setUser(null);
    } finally {
      setLoading(false);
    }
  }

  async function applyLogin(login: LoginVO) {
    tokenStore.set(login.accessToken);
    await refreshMe();
  }

  async function logout() {
    try {
      if (tokenStore.get()) {
        await userApi.logout();
      }
    } finally {
      tokenStore.clear();
      setUser(null);
    }
  }

  useEffect(() => {
    void refreshMe();
  }, []);

  const value = useMemo(
    () => ({
      user,
      loading,
      isAuthenticated: Boolean(user),
      refreshMe,
      applyLogin,
      logout
    }),
    [user, loading]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used inside AuthProvider");
  }
  return context;
}
