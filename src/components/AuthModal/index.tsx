import React, { useMemo } from 'react';
import { ConfigProvider, Modal, theme as antdTheme } from 'antd';
import zhCN from 'antd/locale/zh_CN';

import { AUTH_MODAL_WIDTH, authModalStyles, authModeTitles } from './config';
import AuthModalBody from './parts/AuthModalBody';
import { useAuthModalContent } from './useAuthModalContent';

import './style.less';

function AuthModal() {
  const {
    open,
    mode,
    authTip,
    error,
    loading,
    successRedirecting,
    handleClose,
    handleModeChange,
    clearAuthTip,
    clearError,
    handleLogin,
    handleRegister,
    handleResetPassword,
    handleForgotPassword,
    handleBackToLogin,
  } = useAuthModalContent();

  const lightTheme = useMemo(
    () => ({
      algorithm: antdTheme.defaultAlgorithm,
      token: {
        colorPrimary: '#ff6600',
        borderRadius: 8,
        colorBgBase: '#ffffff',
        colorTextBase: '#14191e',
      },
    }),
    [],
  );

  return (
    <ConfigProvider locale={zhCN} theme={lightTheme}>
      <Modal
        open={open}
        onCancel={handleClose}
        footer={null}
        centered
        destroyOnClose
        width={AUTH_MODAL_WIDTH}
        className="auth-modal"
        rootClassName="auth-modal-root"
        styles={authModalStyles}
        title={authModeTitles[mode]}
      >
        <AuthModalBody
          mode={mode}
          authTip={authTip}
          error={error}
          loading={loading}
          successRedirecting={successRedirecting}
          onModeChange={handleModeChange}
          onClearAuthTip={clearAuthTip}
          onClearError={clearError}
          onLogin={handleLogin}
          onRegister={handleRegister}
          onResetPassword={handleResetPassword}
          onForgotPassword={handleForgotPassword}
          onBackToLogin={handleBackToLogin}
        />
      </Modal>
    </ConfigProvider>
  );
}

export default AuthModal;
