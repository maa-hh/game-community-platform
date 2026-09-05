import React, {
  createContext,
  useCallback,
  useContext,
  useLayoutEffect,
  useMemo,
  useState,
} from 'react';
import { theme as antdTheme } from 'antd';
import type { ThemeConfig } from 'antd';

export type ThemeMode = 'light' | 'dark';

const THEME_STORAGE_KEY = 'game_community_theme';

interface ThemeContextValue {
  /** 用户偏好（持久化），不含路由临时覆盖 */
  preference: ThemeMode;
  /** 实际生效主题（含登录页临时深色） */
  mode: ThemeMode;
  isDark: boolean;
  setMode: (mode: ThemeMode) => void;
  toggleMode: () => void;
  /** 路由级临时主题（如官网首页），不写 localStorage；传 null 清除 */
  setRouteOverride: (mode: ThemeMode | null) => void;
  antdThemeConfig: ThemeConfig;
}

const ThemeContext = createContext<ThemeContextValue | null>(null);

function readInitialMode(): ThemeMode {
  try {
    const saved = localStorage.getItem(THEME_STORAGE_KEY);
    if (saved === 'light' || saved === 'dark') return saved;
  } catch {
    // ignore
  }
  // 站内默认白天
  return 'light';
}

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const [preference, setPreference] = useState<ThemeMode>(readInitialMode);
  const [routeOverride, setRouteOverrideState] = useState<ThemeMode | null>(
    null,
  );

  const mode = routeOverride ?? preference;

  const setMode = useCallback((next: ThemeMode) => {
    setPreference(next);
    setRouteOverrideState(null);
    try {
      localStorage.setItem(THEME_STORAGE_KEY, next);
    } catch {
      // ignore
    }
  }, []);

  const setRouteOverride = useCallback((next: ThemeMode | null) => {
    setRouteOverrideState(next);
  }, []);

  const toggleMode = useCallback(() => {
    const next = mode === 'dark' ? 'light' : 'dark';
    setMode(next);
  }, [mode, setMode]);

  // CSS variables power the layout while ConfigProvider updates antd tokens.
  // Apply the DOM theme before the browser paints so the two surfaces cannot
  // briefly render with different modes.
  useLayoutEffect(() => {
    const root = document.documentElement;
    root.setAttribute('data-theme', mode);
    root.setAttribute('data-theme-switching', 'true');

    const frameId = window.requestAnimationFrame(() => {
      root.removeAttribute('data-theme-switching');
    });

    return () => {
      window.cancelAnimationFrame(frameId);
      root.removeAttribute('data-theme-switching');
    };
  }, [mode]);

  const antdThemeConfig = useMemo<ThemeConfig>(
    () => ({
      algorithm:
        mode === 'dark' ? antdTheme.darkAlgorithm : antdTheme.defaultAlgorithm,
      token: {
        colorPrimary: '#ff6600',
        borderRadius: 8,
        fontSize: 14,
        colorBgBase: mode === 'dark' ? '#14191e' : '#ffffff',
      },
    }),
    [mode],
  );

  const value = useMemo(
    () => ({
      preference,
      mode,
      isDark: mode === 'dark',
      setMode,
      toggleMode,
      setRouteOverride,
      antdThemeConfig,
    }),
    [preference, mode, setMode, toggleMode, setRouteOverride, antdThemeConfig],
  );

  return (
    <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
  );
}

export function useTheme() {
  const ctx = useContext(ThemeContext);
  if (!ctx) {
    throw new Error('useTheme must be used within ThemeProvider');
  }
  return ctx;
}
