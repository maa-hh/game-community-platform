import React from 'react';
import { App as AntdApp, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { Provider } from 'react-redux';
import store from '@/store';
import AppRouter from '@/router';
import { ThemeProvider, useTheme } from '@/hooks/useTheme';
import { AuthModalProvider } from '@/hooks/useAuthModal';
import { useNotificationSse } from '@/hooks/useNotificationSse';

function ThemedApp() {
  const { antdThemeConfig } = useTheme();
  useNotificationSse();

  return (
    <ConfigProvider locale={zhCN} theme={antdThemeConfig}>
      <AntdApp>
        <AuthModalProvider>
          <AppRouter />
        </AuthModalProvider>
      </AntdApp>
    </ConfigProvider>
  );
}

function App() {
  return (
    <Provider store={store}>
      <ThemeProvider>
        <ThemedApp />
      </ThemeProvider>
    </Provider>
  );
}

export default App;
