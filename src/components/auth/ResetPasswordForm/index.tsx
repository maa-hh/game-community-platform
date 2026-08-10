import React, { memo } from 'react';
import type { FC } from 'react';

import ResetPasswordFormFields from './parts/ResetPasswordFormFields';
import type { IProps } from './types';
import { useResetPasswordForm } from './useResetPasswordForm';

const ResetPasswordForm: FC<IProps> = ({
  loading,
  onFinish,
  onBackToLogin,
}) => {
  const { form, emailRef, sendingCode, countdown, syncEmail, handleSendCode } =
    useResetPasswordForm();

  return (
    <ResetPasswordFormFields
      form={form}
      emailRef={emailRef}
      loading={loading}
      sendingCode={sendingCode}
      countdown={countdown}
      onSyncEmail={syncEmail}
      onSendCode={handleSendCode}
      onFinish={onFinish}
      onBackToLogin={onBackToLogin}
    />
  );
};

export default memo(ResetPasswordForm);
