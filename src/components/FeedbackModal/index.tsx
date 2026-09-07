import React, { useEffect, useState } from 'react';
import type { FC } from 'react';
import { App, Form, Input, Modal, Select } from 'antd';

import { submitFeedbackApi } from '@/service/social';
import { formatApiError } from '@/utils/apiError';

import { FEEDBACK_TYPE_OPTIONS } from './constants';
import type { FeedbackFormValues, FeedbackModalProps } from './types';

import './style.less';

const FeedbackModal: FC<FeedbackModalProps> = ({
  open,
  onClose,
  onSuccess,
}) => {
  const { message } = App.useApp();
  const [form] = Form.useForm<FeedbackFormValues>();
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (open) {
      form.resetFields();
      form.setFieldsValue({ feedbackType: FEEDBACK_TYPE_OPTIONS[0].value });
    }
  }, [form, open]);

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSubmitting(true);
      await submitFeedbackApi(values);
      message.success('反馈已提交，感谢你的建议');
      onSuccess?.();
      onClose();
    } catch (error) {
      if (error && typeof error === 'object' && 'errorFields' in error) {
        return;
      }
      message.error(formatApiError('反馈提交失败', error));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      open={open}
      title="问题反馈"
      okText="提交反馈"
      cancelText="取消"
      confirmLoading={submitting}
      destroyOnHidden
      onCancel={onClose}
      onOk={() => void handleSubmit()}
      className="feedback-modal"
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="feedbackType"
          label="反馈类型"
          rules={[{ required: true, message: '请选择反馈类型' }]}
        >
          <Select options={FEEDBACK_TYPE_OPTIONS} />
        </Form.Item>
        <Form.Item
          name="content"
          label="具体内容"
          rules={[
            { required: true, whitespace: true, message: '请填写具体内容' },
            { max: 200, message: '具体内容不能超过 200 字' },
          ]}
        >
          <Input.TextArea
            rows={5}
            maxLength={200}
            showCount
            placeholder="请描述你遇到的问题或想提出的建议"
            onPressEnter={(event) => {
              if (event.shiftKey || event.nativeEvent.isComposing) return;
              event.preventDefault();
              void handleSubmit();
            }}
          />
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default FeedbackModal;
