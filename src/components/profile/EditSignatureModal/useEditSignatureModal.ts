import { useEffect, useState } from 'react';
import { App, Form } from 'antd';

import { useAppDispatch, useAppSelector } from '@/store';
import { updateUserProfile } from '@/store/modules/auth';
import { updateSignatureApi } from '@/service/profile';
import { FIELD_AUDIT, isFieldBusy } from '@/service/types';
import { formatApiError } from '@/utils/apiError';

import { editSignatureModalConfig } from './config';
import type { IEditSignatureFormValues } from './types';

export function useEditSignatureModal(open: boolean, onClose: () => void) {
  const { message } = App.useApp();
  const dispatch = useAppDispatch();
  const { user } = useAppSelector((state) => state.auth);
  const [form] = Form.useForm<IEditSignatureFormValues>();
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!open || !user) return;
    form.setFieldsValue({ signature: user.signature || '' });
  }, [open, user, form]);

  const handleSubmit = async () => {
    if (!user || submitting) return;
    if (isFieldBusy(user.signatureAuditStatus)) {
      message.warning(editSignatureModalConfig.messages.auditing);
      return;
    }
    const values = await form.validateFields();
    const signature = (values.signature || '').trim();
    if (signature === (user.signature || '').trim()) {
      message.info(editSignatureModalConfig.messages.unchanged);
      return;
    }

    setSubmitting(true);
    try {
      const res = await updateSignatureApi(signature, user.version ?? 0);
      dispatch(
        updateUserProfile({
          pendingSignature: res.data.pendingValue ?? signature,
          signatureAuditStatus: FIELD_AUDIT.AUDITING,
          signatureAuditMessage: null,
        }),
      );
      message.success(editSignatureModalConfig.messages.success);
      onClose();
    } catch (error) {
      message.error(
        formatApiError(editSignatureModalConfig.messages.errorPrefix, error),
      );
    } finally {
      setSubmitting(false);
    }
  };

  return {
    form,
    submitting,
    handleSubmit,
  };
}
