import type { Rule } from 'antd/es/form';
import { isValidEmail } from '@/utils/validate';

export type AuthMode = 'login' | 'register' | 'reset';

export interface IResetPasswordFormValues {
  email: string;
  code: string;
  password: string;
  confirmPassword: string;
}

export interface ILoginFormValues {
  email: string;
  password: string;
}

export interface IRegisterFormValues {
  email: string;
  code: string;
  password: string;
  confirmPassword: string;
}

export interface ILoginLocationState {
  from?: string;
  tip?: string;
}

export const SEND_COUNTDOWN = 60;

export const emailFieldRules: Rule[] = [
  { required: true, message: '请输入邮箱', whitespace: true },
  {
    validator: (_: unknown, value: string) => {
      const email = String(value ?? '').trim();
      if (!email) {
        return Promise.reject(new Error('请输入邮箱'));
      }
      return isValidEmail(email)
        ? Promise.resolve()
        : Promise.reject(new Error('请输入正确的邮箱格式'));
    },
  },
];
