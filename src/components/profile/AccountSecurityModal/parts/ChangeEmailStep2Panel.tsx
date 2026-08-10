import React from 'react';
import type { FC } from 'react';
import { Button, Form, Input, Space } from 'antd';
import type { FormInstance } from 'antd/es/form';
import { MailOutlined, SafetyOutlined } from '@ant-design/icons';

import { emailFieldRules } from '@/components/auth/constants';

import { verificationCodeRules } from '../config';
import type { IChangeEmailStep2Values } from '../types';

interface IProps {
  form: FormInstance<IChangeEmailStep2Values>;
  submitting: boolean;
  sendingCode: boolean;
  countdown: number;
  onSendCode: () => void;
  onFinish: (values: IChangeEmailStep2Values) => void;
}

const ChangeEmailStep2Panel: FC<IProps> = ({
  form,
  submitting,
  sendingCode,
  countdown,
  onSendCode,
  onFinish,
}) => {
  return (
    <Form
      form={form}
      layout="vertical"
      onFinish={onFinish}
      disabled={submitting}
      className="account-security__form"
    >
      <Form.Item
        name="newEmail"
        label="新邮箱"
        normalize={(value) =>
          typeof value === 'string' ? value.trim() : value
        }
        rules={emailFieldRules}
      >
        <Input
          prefix={<MailOutlined />}
          placeholder="请输入新邮箱"
          type="email"
          autoComplete="email"
        />
      </Form.Item>

      <Form.Item label="新邮箱验证码" required>
        <Space.Compact className="account-security__code-compact">
          <Form.Item name="newCode" noStyle rules={verificationCodeRules}>
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
            disabled={countdown > 0 || submitting}
          >
            {countdown > 0 ? `${countdown}s` : '获取验证码'}
          </Button>
        </Space.Compact>
      </Form.Item>

      <Button type="primary" htmlType="submit" block loading={submitting}>
        确认更换
      </Button>
    </Form>
  );
};

export default ChangeEmailStep2Panel;
