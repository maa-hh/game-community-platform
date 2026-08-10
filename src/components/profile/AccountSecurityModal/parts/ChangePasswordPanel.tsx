import React from 'react';
import type { FC } from 'react';
import { Button, Form, Input } from 'antd';
import type { FormInstance } from 'antd/es/form';
import { LockOutlined } from '@ant-design/icons';

import { isValidPassword } from '@/utils/validate';

import type { IChangePasswordValues } from '../types';

interface IProps {
  form: FormInstance<IChangePasswordValues>;
  submitting: boolean;
  onFinish: (values: IChangePasswordValues) => void;
}

const ChangePasswordPanel: FC<IProps> = ({ form, submitting, onFinish }) => {
  return (
    <Form
      form={form}
      layout="vertical"
      onFinish={onFinish}
      disabled={submitting}
      className="account-security__form"
    >
      <Form.Item
        name="oldPassword"
        label="原密码"
        rules={[{ required: true, message: '请输入原密码' }]}
      >
        <Input.Password
          prefix={<LockOutlined />}
          placeholder="请输入原密码"
          maxLength={32}
        />
      </Form.Item>

      <Form.Item
        name="newPassword"
        label="新密码"
        rules={[
          { required: true, message: '请输入新密码' },
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
        dependencies={['newPassword']}
        rules={[
          { required: true, message: '请再次输入新密码' },
          ({ getFieldValue }) => ({
            validator: (_, value) => {
              if (!value) return Promise.resolve();
              if (getFieldValue('newPassword') === value) {
                return Promise.resolve();
              }
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

      <Button type="primary" htmlType="submit" block loading={submitting}>
        确认修改
      </Button>
    </Form>
  );
};

export default ChangePasswordPanel;
