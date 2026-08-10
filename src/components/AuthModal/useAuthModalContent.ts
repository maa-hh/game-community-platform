import { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { message } from 'antd';

import { useAppDispatch, useAppSelector } from '@/store';
import {
  loginAction,
  registerAction,
  resetPasswordAction,
  clearAuthError,
  setSuccessRedirecting,
} from '@/store/modules/auth';
import { useAuthModal } from '@/hooks/useAuthModal';
import { consumeAuthTip } from '@/utils/storage';
import type {
  AuthMode,
  ILoginFormValues,
  IRegisterFormValues,
  IResetPasswordFormValues,
  ILoginLocationState,
} from '@/components/auth/constants';

export function useAuthModalContent() {
  const navigate = useNavigate();
  const location = useLocation();
  const dispatch = useAppDispatch();
  const { open, mode, closeAuth, setMode } = useAuthModal();
  const { loading, error, successRedirecting } = useAppSelector(
    (state) => state.auth,
  );
  const locationState = (location.state as ILoginLocationState | null) || null;
  const redirectFromRef = useRef(locationState?.from || '/');
  const lastAuthModeRef = useRef<'login' | 'register'>('login');
  const [authTip, setAuthTip] = useState('');

  useEffect(() => {
    if (!open) return;
    const tip = locationState?.tip || consumeAuthTip() || '';
    if (tip) {
      setAuthTip(tip);
      message.warning(tip);
    }
    redirectFromRef.current = locationState?.from || '/';
  }, [open, locationState?.from, locationState?.tip]);

  useEffect(() => {
    if (!successRedirecting) return;

    dispatch(setSuccessRedirecting(false));
    closeAuth();
    const redirectTo = redirectFromRef.current;
    navigate(redirectTo === '/login' ? '/' : redirectTo, { replace: true });
    message.success(
      lastAuthModeRef.current === 'register' ? '注册成功' : '登录成功',
      3,
    );
  }, [successRedirecting, dispatch, navigate, closeAuth]);

  const handleModeChange = (value: string | number) => {
    if (value === 'reset') return;
    setMode(value as AuthMode);
    dispatch(clearAuthError());
  };

  const handleClose = () => {
    dispatch(clearAuthError());
    setAuthTip('');
    closeAuth();
  };

  const handleForgotPassword = () => {
    setMode('reset');
    dispatch(clearAuthError());
  };

  const handleBackToLogin = () => {
    setMode('login');
    dispatch(clearAuthError());
  };

  const handleResetPassword = async (values: IResetPasswordFormValues) => {
    try {
      await dispatch(
        resetPasswordAction({
          email: values.email,
          password: values.password,
          code: values.code,
        }),
      ).unwrap();
      message.success('密码已重置，请使用新密码登录');
      handleBackToLogin();
    } catch {
      // store 展示 error
    }
  };

  const handleLogin = (values: ILoginFormValues) => {
    lastAuthModeRef.current = 'login';
    dispatch(loginAction(values));
  };

  const handleRegister = (values: IRegisterFormValues) => {
    lastAuthModeRef.current = 'register';
    dispatch(
      registerAction({
        email: values.email,
        password: values.password,
        code: values.code,
      }),
    );
  };

  return {
    open,
    mode,
    authTip,
    error,
    loading,
    successRedirecting,
    handleClose,
    handleModeChange,
    clearAuthTip: () => setAuthTip(''),
    clearError: () => dispatch(clearAuthError()),
    handleLogin,
    handleRegister,
    handleResetPassword,
    handleForgotPassword,
    handleBackToLogin,
  };
}
