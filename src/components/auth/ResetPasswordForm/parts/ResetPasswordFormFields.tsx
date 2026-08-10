import React from 'react';
import type { FC } from 'react';
import { Button, Form, Input, Space, Typography } from 'antd';
import type { FormInstance } from 'antd/es/form';
import type { InputRef } from 'antd/es/input';
import { LockOutlined, MailOutlined, SafetyOutlined } from '@ant-design/icons';

import { emailFieldRules } from '@/components/auth/constants';
import type { IResetPasswordFormValues } from '@/components/auth/constants';
import { isValidPassword } from '@/utils/validate';

const { Text, Link } = Typography;

interface IProps {
  form: FormInstance<IResetPasswordFormValues>;
  emailRef: React.RefObject<InputRef | null>;
  loading: boolean;
  sendingCode: boolean;
  countdown: number;
  onSyncEmail: () => void;
  onSendCode: () => void;
  onFinish: (values: IResetPasswordFormValues) => void;
  onBackToLogin: () => void;
}

const ResetPasswordFormFields: FC<IProps> = ({
  form,
  emailRef,
  loading,
  sendingCode,
  countdown,
  onSyncEmail,
  onSendCode,
  onFinish,
  onBackToLogin,
}) => {
  return (
    <Form
      form={form}
      layout="vertical"
      onFinish={onFinish}
      className="reset-password-form"
    >
      <Form.Item
        name="email"
        label="邮箱"
        validateTrigger={['onChange', 'onBlur']}
        normalize={(value) =>
          typeof value === 'string' ? value.trim() : value
        }
        rules={emailFieldRules}
      >
        <Input
          ref={emailRef}
          prefix={<MailOutlined />}
          placeholder="请输入注册邮箱"
          type="email"
          autoComplete="email"
          onBlur={onSyncEmail}
        />
      </Form.Item>

      <Form.Item label="验证码" required>
        <Space.Compact className="login-page__code-compact">
          <Form.Item
            name="code"
            noStyle
            rules={[
              { required: true, message: '请输入验证码' },
              {
                validator: (_, value) => {
                  if (!value) return Promise.resolve();
                  return /^\d{6}$/.test(value)
                    ? Promise.resolve()
                    : Promise.reject(new Error('验证码为 6 位数字'));
                },
              },
            ]}
          >
            <Input
              prefix={<SafetyOutlined />}
              placeholder="6 位验证码"
              maxLength={6}
            />
          </Form.Item>
          <Button
            type="default"
            htmlType="button"
            onClick={onSendCode}
            loading={sendingCode}
            disabled={countdown > 0}
            className="login-page__send-code"
          >
            {countdown > 0 ? `${countdown}s` : '发送验证码'}
          </Button>
        </Space.Compact>
      </Form.Item>

      <Form.Item
        name="password"
        label="新密码"
        rules={[
          { required: true, message: '请设置新密码' },
          {
            validator: (_, value) =>
              !value || isValidPassword(value)
                ? Promise.resolve()
                : Promise.reject(
                    new Error('密码为 6-10 位数字、字母或特殊字符'),
                  ),
          },
        ]}
      >
        <Input.Password
          prefix={<LockOutlined />}
          placeholder="6-10 位数字、字母或特殊字符"
          maxLength={10}
        />
      </Form.Item>

      <Form.Item
        name="confirmPassword"
        label="确认新密码"
        dependencies={['password']}
        rules={[
          { required: true, message: '请再次输入新密码' },
          ({ getFieldValue }) => ({
            validator: (_, value) => {
              if (!value) return Promise.resolve();
              if (getFieldValue('password') === value) return Promise.resolve();
              return Promise.reject(new Error('两次输入的密码不一致'));
            },
          }),
        ]}
      >
        <Input.Password
          prefix={<LockOutlined />}
          placeholder="请再次输入新密码"
          maxLength={10}
        />
      </Form.Item>

      <Form.Item className="login-page__submit">
        <Button type="primary" htmlType="submit" block loading={loading}>
          重置密码
        </Button>
      </Form.Item>

      <Text type="secondary">
        <Link onClick={onBackToLogin}>返回登录</Link>
      </Text>
    </Form>
  );
};

export default ResetPasswordFormFields;
