import React, { memo } from 'react';
import type { FC } from 'react';
import { Form, Input, Button, Typography } from 'antd';
import { LockOutlined, MailOutlined } from '@ant-design/icons';

import { isValidPassword } from '@/utils/validate';
import { emailFieldRules } from '@/components/auth/constants';

import type { IProps } from './types';

const { Text, Link } = Typography;

const LoginForm: FC<IProps> = ({ loading, onFinish, onForgotPassword }) => {
  return (
    <Form
      layout="vertical"
      onFinish={onFinish}
      autoComplete="off"
      className="login-form"
    >
      <Form.Item
        name="email"
        label="邮箱"
        normalize={(value) =>
          typeof value === 'string' ? value.trim() : value
        }
        rules={emailFieldRules}
      >
        <Input
          prefix={<MailOutlined />}
          placeholder="请输入邮箱"
          type="email"
        />
      </Form.Item>

      <Form.Item
        name="password"
        label="密码"
        rules={[
          { required: true, message: '请输入密码' },
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
          placeholder="请输入密码"
          maxLength={10}
        />
      </Form.Item>

      <Form.Item className="login-page__submit">
        <Button type="primary" htmlType="submit" block loading={loading}>
          登录
        </Button>
      </Form.Item>

      {onForgotPassword && (
        <div className="login-page__forgot">
          <Link onClick={onForgotPassword}>忘记密码？</Link>
        </div>
      )}

      <Text type="secondary" className="login-page__hint">
        测试账号：test@game.com / abc123
      </Text>
    </Form>
  );
};

export default memo(LoginForm);
