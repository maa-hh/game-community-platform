import React from 'react';
import { App as AntdApp, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { Provider } from 'react-redux';
import store from '@/store';
import AppRouter from '@/router';
import { ThemeProvider, useTheme } from '@/hooks/useTheme';
import { AuthModalProvider } from '@/hooks/useAuthModal';
import { useNotificationSse } from '@/hooks/useNotificationSse';
import { useProfileAuditPoll } from '@/hooks/useProfileAuditPoll';
import { useCrossTabAuthSync } from '@/hooks/useCrossTabAuthSync';

function ThemedAppContent() {
  useCrossTabAuthSync();
  useNotificationSse();
  useProfileAuditPoll();

  return (
    <AuthModalProvider>
      <AppRouter />
    </AuthModalProvider>
  );
}

function ThemedApp() {
  const { antdThemeConfig } = useTheme();

  return (
    <ConfigProvider locale={zhCN} theme={antdThemeConfig}>
      <AntdApp>
        <ThemedAppContent />
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
