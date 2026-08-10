import React, { memo } from 'react';
import type { FC } from 'react';

import RegisterFormFields from './parts/RegisterFormFields';
import type { IProps } from './types';
import { useRegisterForm } from './useRegisterForm';

const RegisterForm: FC<IProps> = ({ loading, onFinish }) => {
  const { form, emailRef, sendingCode, countdown, syncEmail, handleSendCode } =
    useRegisterForm();

  return (
    <RegisterFormFields
      form={form}
      emailRef={emailRef}
      loading={loading}
      sendingCode={sendingCode}
      countdown={countdown}
      onSyncEmail={syncEmail}
      onSendCode={handleSendCode}
      onFinish={onFinish}
    />
  );
};

export default memo(RegisterForm);
