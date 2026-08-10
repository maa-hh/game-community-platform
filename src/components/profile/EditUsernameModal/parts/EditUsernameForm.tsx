import React from 'react';
import type { FC } from 'react';
import { Form, Input } from 'antd';
import type { FormInstance } from 'antd/es/form';

import { countMeaningfulChars, clipByMeaningfulChars } from '@/utils/textCount';

import { USERNAME_MAX, USERNAME_MIN, editUsernameModalConfig } from '../config';
import type { IEditUsernameFormValues } from '../types';

interface IProps {
  form: FormInstance<IEditUsernameFormValues>;
  onSubmit: () => void;
}

const EditUsernameForm: FC<IProps> = ({ form, onSubmit }) => {
  return (
    <Form
      form={form}
      layout="vertical"
      requiredMark={false}
      onFinish={onSubmit}
    >
      <Form.Item
        name="username"
        label={editUsernameModalConfig.fieldLabel}
        rules={[
          {
            validator: (_, value) => {
              const len = countMeaningfulChars(value);
              if (len < USERNAME_MIN) {
                return Promise.reject(
                  new Error(`昵称至少 ${USERNAME_MIN} 个字符（空格不计）`),
                );
              }
              if (len > USERNAME_MAX) {
                return Promise.reject(
                  new Error(`昵称不能超过 ${USERNAME_MAX} 个字符（空格不计）`),
                );
              }
              return Promise.resolve();
            },
          },
        ]}
      >
        <Input
          placeholder={editUsernameModalConfig.placeholder}
          allowClear
          onPressEnter={(e) => {
            e.preventDefault();
            void onSubmit();
          }}
          count={{
            show: true,
            max: USERNAME_MAX,
            strategy: countMeaningfulChars,
            exceedFormatter: (value, { max }) =>
              clipByMeaningfulChars(value, max),
          }}
        />
      </Form.Item>
    </Form>
  );
};

export default EditUsernameForm;
