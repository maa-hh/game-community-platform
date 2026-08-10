import React from 'react';
import type { FC } from 'react';
import { Form, Input } from 'antd';
import type { FormInstance } from 'antd/es/form';

import { countMeaningfulChars, clipByMeaningfulChars } from '@/utils/textCount';

import { SIGNATURE_MAX, editSignatureModalConfig } from '../config';
import type { IEditSignatureFormValues } from '../types';

interface IProps {
  form: FormInstance<IEditSignatureFormValues>;
  onSubmit: () => void;
}

const EditSignatureForm: FC<IProps> = ({ form, onSubmit }) => {
  return (
    <Form
      form={form}
      layout="vertical"
      requiredMark={false}
      onFinish={onSubmit}
    >
      <Form.Item
        name="signature"
        label={editSignatureModalConfig.fieldLabel}
        extra={editSignatureModalConfig.fieldExtra}
        rules={[
          {
            validator: (_, value) => {
              const len = countMeaningfulChars(value);
              if (len > SIGNATURE_MAX) {
                return Promise.reject(
                  new Error(
                    `个性签名不能超过 ${SIGNATURE_MAX} 个字符（空格/换行不计）`,
                  ),
                );
              }
              return Promise.resolve();
            },
          },
        ]}
      >
        <Input.TextArea
          placeholder={editSignatureModalConfig.placeholder}
          autoSize={{ minRows: 2, maxRows: 4 }}
          allowClear
          onPressEnter={(e) => {
            if (e.shiftKey) return;
            e.preventDefault();
            void onSubmit();
          }}
          count={{
            show: true,
            max: SIGNATURE_MAX,
            strategy: countMeaningfulChars,
            exceedFormatter: (value, { max }) =>
              clipByMeaningfulChars(value, max),
          }}
        />
      </Form.Item>
    </Form>
  );
};

export default EditSignatureForm;
