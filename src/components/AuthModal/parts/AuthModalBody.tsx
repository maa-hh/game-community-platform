import React, { memo } from 'react';
import type { FC } from 'react';
import { Alert, Segmented, Typography } from 'antd';

import LoginForm from '@/components/auth/LoginForm';
import RegisterForm from '@/components/auth/RegisterForm';
import ResetPasswordForm from '@/components/auth/ResetPasswordForm';
import { authModeTabs, resetPasswordHint } from '../config';
import type { AuthModalBodyProps } from '../types';

const { Text } = Typography;

const AuthModalBody: FC<AuthModalBodyProps> = ({
  mode,
  authTip,
  error,
  loading,
  successRedirecting,
  onModeChange,
  onClearAuthTip,
  onClearError,
  onLogin,
  onRegister,
  onResetPassword,
  onForgotPassword,
  onBackToLogin,
}) => (
  <div className="auth-modal__body">
    {mode !== 'reset' && (
      <Segmented
        block
        value={mode}
        onChange={onModeChange}
        options={authModeTabs}
        className="auth-modal__tabs"
      />
    )}

    {authTip && (
      <Alert
        type="warning"
        message={authTip}
        showIcon
        closable
        onClose={onClearAuthTip}
        className="auth-modal__alert"
      />
    )}

    {error && (
      <Alert
        type="error"
        message={error}
        showIcon
        closable
        onClose={onClearError}
        className="auth-modal__alert"
      />
    )}

    {mode === 'login' ? (
      <LoginForm
        loading={loading || successRedirecting}
        onFinish={onLogin}
        onForgotPassword={onForgotPassword}
      />
    ) : mode === 'register' ? (
      <RegisterForm
        loading={loading || successRedirecting}
        onFinish={onRegister}
      />
    ) : (
      <ResetPasswordForm
        loading={loading}
        onFinish={onResetPassword}
        onBackToLogin={onBackToLogin}
      />
    )}

    {mode === 'reset' && (
      <Text type="secondary" className="auth-modal__hint">
        {resetPasswordHint}
      </Text>
    )}
  </div>
);

export default memo(AuthModalBody);
