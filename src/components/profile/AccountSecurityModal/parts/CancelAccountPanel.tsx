import React from 'react';
import type { FC } from 'react';
import { Alert, Button, Form, Input, Space, Typography } from 'antd';
import type { FormInstance } from 'antd/es/form';
import { SafetyOutlined } from '@ant-design/icons';

import { verificationCodeRules } from '../config';
import type { ICancelAccountValues } from '../types';

const { Text } = Typography;

interface IProps {
  form: FormInstance<ICancelAccountValues>;
  currentEmail?: string;
  submitting: boolean;
  sendingCode: boolean;
  countdown: number;
  onSendCode: () => void;
  onFinish: (values: ICancelAccountValues) => void;
}

const CancelAccountPanel: FC<IProps> = ({
  form,
  currentEmail,
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
      <Alert
        type="warning"
        showIcon
        className="account-security__alert"
        message="注销预警"
        description="确认后账号将进入 7 天冷静期。一周内登录可自动取消注销；期满后账号停用。"
      />

      <Text type="secondary" className="account-security__current">
        验证邮箱：{currentEmail || '未绑定'}
      </Text>

      <Form.Item label="验证码" required>
        <Space.Compact className="account-security__code-compact">
          <Form.Item name="code" noStyle rules={verificationCodeRules}>
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
            {countdown > 0 ? `${countdown}s` : '发送验证码'}
          </Button>
        </Space.Compact>
      </Form.Item>

      <Button
        type="primary"
        danger
        htmlType="submit"
        block
        loading={submitting}
      >
        确定注销
      </Button>
    </Form>
  );
};

export default CancelAccountPanel;
