import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Form, Modal, message } from 'antd';

import { useAppDispatch, useAppSelector } from '@/store';
import { logout } from '@/store/modules/auth';
import {
  cancelAccountApi,
  changePasswordApi,
  confirmChangeEmailApi,
  prepareChangeEmailApi,
  sendCancelAccountCodeApi,
  sendChangeEmailOldCodeApi,
} from '@/service/account';
import { formatApiError } from '@/utils/apiError';
import { setAuthTip } from '@/utils/storage';
import { SEND_COUNTDOWN } from '@/components/auth/constants';

import type {
  EmailStep,
  ICancelAccountValues,
  IChangeEmailStep1Values,
  IChangeEmailStep2Values,
  IChangePasswordValues,
  PanelKey,
} from './types';

export function useAccountSecurityModal(open: boolean, onClose: () => void) {
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { user } = useAppSelector((state) => state.auth);
  const [panel, setPanel] = useState<PanelKey>('menu');
  const [emailStep, setEmailStep] = useState<EmailStep>(1);
  const [verifiedOldCode, setVerifiedOldCode] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [sendingCode, setSendingCode] = useState(false);
  const [countdown, setCountdown] = useState(0);

  const [passwordForm] = Form.useForm<IChangePasswordValues>();
  const [emailStep1Form] = Form.useForm<IChangeEmailStep1Values>();
  const [emailStep2Form] = Form.useForm<IChangeEmailStep2Values>();
  const [cancelForm] = Form.useForm<ICancelAccountValues>();

  useEffect(() => {
    if (!open) return;
    setPanel('menu');
    setEmailStep(1);
    setVerifiedOldCode('');
    setSubmitting(false);
    setSendingCode(false);
    setCountdown(0);
    passwordForm.resetFields();
    emailStep1Form.resetFields();
    emailStep2Form.resetFields();
    cancelForm.resetFields();
  }, [open, passwordForm, emailStep1Form, emailStep2Form, cancelForm]);

  useEffect(() => {
    if (countdown <= 0) return;
    const timer = window.setInterval(() => {
      setCountdown((prev) => (prev <= 1 ? 0 : prev - 1));
    }, 1000);
    return () => window.clearInterval(timer);
  }, [countdown]);

  const forceToLogin = useCallback(
    (tip: string) => {
      setAuthTip(tip);
      dispatch(logout());
      onClose();
      navigate('/', { replace: true });
      message.success(tip);
    },
    [dispatch, navigate, onClose],
  );

  const handleMenuClick = (key: Exclude<PanelKey, 'menu'>) => {
    if (key === 'cancel') {
      Modal.confirm({
        title: '确认注销吗？',
        content:
          '注销后将进入 7 天冷静期。冷静期内登录可自动取消注销；期满账号将被停用。',
        okText: '确认注销',
        cancelText: '再想想',
        okButtonProps: { danger: true },
        centered: true,
        onOk: () => {
          cancelForm.resetFields();
          setCountdown(0);
          setPanel('cancel');
        },
      });
      return;
    }
    setCountdown(0);
    if (key === 'email') {
      setEmailStep(1);
      setVerifiedOldCode('');
      emailStep1Form.resetFields();
      emailStep2Form.resetFields();
    }
    setPanel(key);
  };

  const handleBack = () => {
    if (submitting) return;
    if (panel === 'email' && emailStep === 2) {
      setEmailStep(1);
      setCountdown(0);
      return;
    }
    setPanel('menu');
    setCountdown(0);
  };

  const handleSendOldEmailCode = async () => {
    if (!user?.email) {
      message.error('当前账号未绑定邮箱');
      return;
    }
    try {
      setSendingCode(true);
      await sendChangeEmailOldCodeApi();
      message.success('验证码已发送至当前邮箱');
      setCountdown(SEND_COUNTDOWN);
    } catch (err) {
      message.error(formatApiError('验证码发送失败', err));
    } finally {
      setSendingCode(false);
    }
  };

  const handleEmailStep1Next = async (values: IChangeEmailStep1Values) => {
    setVerifiedOldCode(values.oldCode);
    setEmailStep(2);
    setCountdown(0);
    emailStep2Form.resetFields();
  };

  const handleSendNewEmailCode = async () => {
    try {
      const values = await emailStep2Form.validateFields(['newEmail']);
      const newEmail = String(values.newEmail ?? '').trim();
      if (user?.email && newEmail.toLowerCase() === user.email.toLowerCase()) {
        message.error('新邮箱不能与当前邮箱相同');
        return;
      }
      if (!verifiedOldCode) {
        message.error('请先完成原邮箱验证');
        setEmailStep(1);
        return;
      }
      setSendingCode(true);
      await prepareChangeEmailApi({
        oldCode: verifiedOldCode,
        newEmail,
      });
      message.success('验证码已发送至新邮箱');
      setCountdown(SEND_COUNTDOWN);
    } catch (err) {
      if (err && typeof err === 'object' && 'errorFields' in err) return;
      message.error(formatApiError('验证码发送失败', err));
    } finally {
      setSendingCode(false);
    }
  };

  const handleSendCancelCode = async () => {
    if (!user?.email) {
      message.error('当前账号未绑定邮箱');
      return;
    }
    try {
      setSendingCode(true);
      await sendCancelAccountCodeApi();
      message.success('验证码已发送至当前邮箱');
      setCountdown(SEND_COUNTDOWN);
    } catch (err) {
      message.error(formatApiError('验证码发送失败', err));
    } finally {
      setSendingCode(false);
    }
  };

  const handleChangePassword = async (values: IChangePasswordValues) => {
    setSubmitting(true);
    try {
      await changePasswordApi({
        oldPassword: values.oldPassword,
        newPassword: values.newPassword,
        confirmPassword: values.confirmPassword,
      });
      forceToLogin('密码已修改，请重新登录');
    } catch (err) {
      message.error(formatApiError('修改密码失败', err));
    } finally {
      setSubmitting(false);
    }
  };

  const handleConfirmChangeEmail = async (values: IChangeEmailStep2Values) => {
    if (!verifiedOldCode) {
      message.error('请先完成原邮箱验证');
      setEmailStep(1);
      return;
    }
    setSubmitting(true);
    try {
      await confirmChangeEmailApi({
        oldCode: verifiedOldCode,
        newEmail: values.newEmail.trim(),
        newCode: values.newCode,
      });
      forceToLogin('邮箱已修改，请重新登录');
    } catch (err) {
      message.error(formatApiError('修改邮箱失败', err));
    } finally {
      setSubmitting(false);
    }
  };

  const handleCancelAccount = async (values: ICancelAccountValues) => {
    setSubmitting(true);
    try {
      await cancelAccountApi({ code: values.code });
      setAuthTip('一周内登录将自动取消注销');
      dispatch(logout());
      onClose();
      navigate('/', { replace: true });
      message.warning('已申请注销：一周内登录可自动取消注销');
    } catch (err) {
      message.error(formatApiError('注销失败', err));
    } finally {
      setSubmitting(false);
    }
  };

  return {
    user,
    panel,
    emailStep,
    submitting,
    sendingCode,
    countdown,
    passwordForm,
    emailStep1Form,
    emailStep2Form,
    cancelForm,
    handleMenuClick,
    handleBack,
    handleSendOldEmailCode,
    handleEmailStep1Next,
    handleSendNewEmailCode,
    handleSendCancelCode,
    handleChangePassword,
    handleConfirmChangeEmail,
    handleCancelAccount,
  };
}
