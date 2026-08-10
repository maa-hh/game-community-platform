import type { Rule } from 'antd/es/form';

import type { EmailStep, PanelKey, SecurityMenuItem } from './types';

export const securityMenuItems: SecurityMenuItem[] = [
  {
    key: 'password',
    title: '修改密码',
    desc: '验证原密码后设置新密码',
    icon: 'lock',
  },
  {
    key: 'email',
    title: '修改邮箱',
    desc: '原邮箱验证后更换新邮箱',
    icon: 'mail',
  },
  {
    key: 'cancel',
    title: '注销账号',
    desc: '进入 7 天冷静期，期间登录可撤销',
    danger: true,
    icon: 'delete',
  },
];

export function getSecurityTitle(
  panel: PanelKey,
  emailStep: EmailStep,
): string {
  const titleMap: Record<PanelKey, string> = {
    menu: '账号与安全',
    password: '修改密码',
    email: emailStep === 1 ? '修改邮箱 · 验证原邮箱' : '修改邮箱 · 绑定新邮箱',
    cancel: '注销账号',
  };
  return titleMap[panel];
}

export const verificationCodeRules: Rule[] = [
  { required: true, message: '请输入验证码' },
  {
    validator: (_, value) => {
      if (!value) return Promise.resolve();
      return /^\d{6}$/.test(value)
        ? Promise.resolve()
        : Promise.reject(new Error('验证码为 6 位数字'));
    },
  },
];
