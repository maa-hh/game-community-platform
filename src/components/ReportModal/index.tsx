import React, { useEffect, useState } from 'react';
import type { FC } from 'react';
import { Form, Input, Modal, Select, message } from 'antd';

import { reportTargetApi } from '@/service/social';
import { formatApiError } from '@/utils/apiError';

import {
  REPORT_CATEGORIES,
  buildReportReason,
  type ReportCategoryValue,
} from './constants';
import type { ReportModalProps } from './types';

import './style.less';

const TARGET_LABEL: Record<ReportModalProps['targetType'], string> = {
  article: '帖子',
  comment: '评论',
  reply: '回复',
  user: '用户',
  danmaku: '弹幕',
};

const ReportModal: FC<ReportModalProps> = ({
  open,
  targetType,
  targetId,
  title,
  onClose,
  onSuccess,
}) => {
  const [form] = Form.useForm<{
    category: ReportCategoryValue;
    detail?: string;
  }>();
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (open) {
      form.resetFields();
      form.setFieldsValue({ category: 'spam' });
    }
  }, [form, open]);

  const handleSubmit = async () => {
    const values = await form.validateFields();
    const category = REPORT_CATEGORIES.find((c) => c.value === values.category);
    const reason = buildReportReason(category?.label || '其他', values.detail);
    setSubmitting(true);
    try {
      await reportTargetApi({
        targetType,
        targetId,
        reason,
      });
      message.success('举报已提交，我们会尽快处理');
      onSuccess?.();
      onClose();
    } catch (err) {
      message.error(formatApiError('举报提交失败', err));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal
      open={open}
      title={title || `举报${TARGET_LABEL[targetType]}`}
      okText="提交举报"
      cancelText="取消"
      confirmLoading={submitting}
      destroyOnClose
      onCancel={onClose}
      onOk={() => void handleSubmit()}
      className="report-modal"
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="category"
          label="举报类别"
          rules={[{ required: true, message: '请选择举报类别' }]}
        >
          <Select
            className="report-modal__category"
            options={REPORT_CATEGORIES.map((item) => ({
              value: item.value,
              label: item.label,
            }))}
          />
        </Form.Item>
        <Form.Item
          name="detail"
          label="补充说明"
          className="report-modal__detail"
          rules={[{ max: 200, message: '补充说明不能超过 200 字' }]}
        >
          <Input.TextArea
            rows={4}
            placeholder="请描述具体问题，便于我们更快处理（选填）"
            maxLength={200}
            showCount
          />
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default ReportModal;
