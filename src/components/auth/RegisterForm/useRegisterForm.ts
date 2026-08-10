import { useCallback, useEffect, useRef, useState } from 'react';
import { Form, message } from 'antd';
import type { InputRef } from 'antd/es/input';

import { useAppDispatch } from '@/store';
import { sendCodeAction } from '@/store/modules/auth';
import { formatApiError } from '@/utils/apiError';
import { SEND_COUNTDOWN } from '@/components/auth/constants';
import type { IRegisterFormValues } from '@/components/auth/constants';

export function useRegisterForm() {
  const dispatch = useAppDispatch();
  const [form] = Form.useForm<IRegisterFormValues>();
  const emailRef = useRef<InputRef>(null);
  const [sendingCode, setSendingCode] = useState(false);
  const [countdown, setCountdown] = useState(0);

  useEffect(() => {
    if (countdown <= 0) return;
    const timer = setInterval(() => {
      setCountdown((prev) => (prev <= 1 ? 0 : prev - 1));
    }, 1000);
    return () => clearInterval(timer);
  }, [countdown]);

  const syncEmail = useCallback(() => {
    const inputValue = emailRef.current?.input?.value?.trim();
    if (inputValue) {
      form.setFieldsValue({ email: inputValue });
    }
  }, [form]);

  const handleSendCode = useCallback(async () => {
    try {
      syncEmail();
      const values = await form.validateFields(['email']);
      const email = String(values.email ?? '').trim();
      setSendingCode(true);
      await dispatch(sendCodeAction({ email, bizType: 'REGISTER' })).unwrap();
      message.success('验证码已发送，请查收邮箱');
      setCountdown(SEND_COUNTDOWN);
    } catch (err) {
      if (err && typeof err === 'object' && 'errorFields' in err) {
        return;
      }
      message.error(formatApiError('验证码发送失败', err));
    } finally {
      setSendingCode(false);
    }
  }, [dispatch, form, syncEmail]);

  return {
    form,
    emailRef,
    sendingCode,
    countdown,
    syncEmail,
    handleSendCode,
  };
}
