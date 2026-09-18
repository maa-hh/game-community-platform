import { useEffect, useRef, useState } from 'react';
import { App, Upload } from 'antd';
import type { UploadProps } from 'antd';

import { useAppDispatch, useAppSelector } from '@/store';
import { updateUserProfile } from '@/store/modules/auth';
import { uploadAvatarApi } from '@/service/profile';
import { FIELD_AUDIT, isFieldBusy } from '@/service/types';
import { formatApiError } from '@/utils/apiError';

import {
  ACCEPT_TYPES,
  MAX_AVATAR_BYTES,
  avatarViewerModalConfig,
} from './config';

export function useAvatarViewerModal(onClose: () => void) {
  const { message } = App.useApp();
  const dispatch = useAppDispatch();
  const { user } = useAppSelector((state) => state.auth);
  const [cropSrc, setCropSrc] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const cropObjectUrlRef = useRef<string | null>(null);

  const avatarBusy = isFieldBusy(user?.avatarAuditStatus);

  useEffect(() => {
    return () => {
      if (cropObjectUrlRef.current) {
        URL.revokeObjectURL(cropObjectUrlRef.current);
        cropObjectUrlRef.current = null;
      }
    };
  }, []);

  const clearCropObjectUrl = () => {
    if (cropObjectUrlRef.current) {
      URL.revokeObjectURL(cropObjectUrlRef.current);
      cropObjectUrlRef.current = null;
    }
  };

  const beforeUpload: UploadProps['beforeUpload'] = (file) => {
    if (avatarBusy) {
      message.warning(avatarViewerModalConfig.messages.auditing);
      return Upload.LIST_IGNORE;
    }
    if (!ACCEPT_TYPES.includes(file.type as (typeof ACCEPT_TYPES)[number])) {
      message.error(avatarViewerModalConfig.messages.invalidType);
      return Upload.LIST_IGNORE;
    }
    if (file.size > MAX_AVATAR_BYTES) {
      message.error(avatarViewerModalConfig.messages.tooLarge);
      return Upload.LIST_IGNORE;
    }

    clearCropObjectUrl();
    const objectUrl = URL.createObjectURL(file);
    cropObjectUrlRef.current = objectUrl;
    setCropSrc(objectUrl);
    return false;
  };

  const handleCropConfirm = async (_dataUrl: string, blob: Blob) => {
    setCropSrc(null);
    clearCropObjectUrl();
    const file = new File([blob], 'avatar.jpg', {
      type: blob.type || 'image/jpeg',
    });

    setUploading(true);
    try {
      const result = await uploadAvatarApi(file, user?.version ?? 0);
      dispatch(
        updateUserProfile({
          pendingAvatarUrl: result.data.pendingValue,
          avatarAuditStatus: FIELD_AUDIT.AUDITING,
          avatarAuditMessage: null,
        }),
      );
      message.success(avatarViewerModalConfig.messages.success);
      onClose();
    } catch (error) {
      message.error(
        formatApiError(avatarViewerModalConfig.messages.errorPrefix, error),
      );
    } finally {
      setUploading(false);
    }
  };

  const handleCropCancel = () => {
    setCropSrc(null);
    clearCropObjectUrl();
  };

  return {
    cropSrc,
    uploading,
    avatarBusy,
    beforeUpload,
    handleCropConfirm,
    handleCropCancel,
  };
}
