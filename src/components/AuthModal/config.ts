import type { AuthMode } from '@/components/auth/constants';

export const AUTH_MODAL_WIDTH = 420;

export const authModeTitles: Record<AuthMode, string> = {
  login: '登录账号',
  register: '注册账号',
  reset: '找回密码',
};

export const authModeTabs = [
  { label: '登录', value: 'login' as const },
  { label: '注册', value: 'register' as const },
];

export const authModalStyles = {
  header: {
    background: '#ffffff',
    color: '#14191e',
    borderBottom: '1px solid #f0f0f0',
    marginBottom: 0,
    paddingBottom: 12,
  },
  body: {
    background: '#ffffff',
    color: '#14191e',
  },
};

export const resetPasswordHint = '验证码将发送到你的注册邮箱';
