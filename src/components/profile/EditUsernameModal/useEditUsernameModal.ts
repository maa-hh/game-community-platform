import { useEffect, useState } from 'react';
import { Form, message } from 'antd';

import { useAppDispatch, useAppSelector } from '@/store';
import { updateUserProfile } from '@/store/modules/auth';
import { updateUsernameApi } from '@/service/profile';
import { FIELD_AUDIT, isFieldBusy } from '@/service/types';
import { formatApiError } from '@/utils/apiError';

import { editUsernameModalConfig } from './config';
import type { IEditUsernameFormValues } from './types';

export function useEditUsernameModal(open: boolean, onClose: () => void) {
  const dispatch = useAppDispatch();
  const { user } = useAppSelector((state) => state.auth);
  const [form] = Form.useForm<IEditUsernameFormValues>();
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!open || !user) return;
    form.setFieldsValue({ username: user.username || '' });
  }, [open, user, form]);

  const handleSubmit = async () => {
    if (!user || submitting) return;
    if (isFieldBusy(user.usernameAuditStatus)) {
      message.warning(editUsernameModalConfig.messages.auditing);
      return;
    }
    const values = await form.validateFields();
    const username = values.username.trim();
    if (username === user.username) {
      message.info(editUsernameModalConfig.messages.unchanged);
      return;
    }

    setSubmitting(true);
    try {
      const res = await updateUsernameApi(username, user.version ?? 0);
      dispatch(
        updateUserProfile({
          pendingUsername: res.data.pendingValue ?? username,
          usernameAuditStatus: FIELD_AUDIT.AUDITING,
          usernameAuditMessage: null,
        }),
      );
      message.success(editUsernameModalConfig.messages.success);
      onClose();
    } catch (error) {
      message.error(
        formatApiError(editUsernameModalConfig.messages.errorPrefix, error),
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
