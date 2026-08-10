import React from 'react';
import type { FC } from 'react';
import { Button, Form, Input, Space, Typography } from 'antd';
import type { FormInstance } from 'antd/es/form';
import { SafetyOutlined } from '@ant-design/icons';

import { verificationCodeRules } from '../config';
import type { IChangeEmailStep1Values } from '../types';

const { Text } = Typography;

interface IProps {
  form: FormInstance<IChangeEmailStep1Values>;
  currentEmail?: string;
  submitting: boolean;
  sendingCode: boolean;
  countdown: number;
  onSendCode: () => void;
  onFinish: (values: IChangeEmailStep1Values) => void;
}

const ChangeEmailStep1Panel: FC<IProps> = ({
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
      <Text type="secondary" className="account-security__current">
        当前邮箱：{currentEmail || '未绑定'}
      </Text>

      <Form.Item label="原邮箱验证码" required>
        <Space.Compact className="account-security__code-compact">
          <Form.Item name="oldCode" noStyle rules={verificationCodeRules}>
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

      <Button type="primary" htmlType="submit" block>
        下一步
      </Button>
    </Form>
  );
};

export default ChangeEmailStep1Panel;
