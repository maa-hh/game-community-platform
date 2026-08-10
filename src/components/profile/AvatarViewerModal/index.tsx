import React, { memo } from 'react';
import type { FC } from 'react';
import { Modal } from 'antd';

import AvatarCropper from '@/components/profile/AvatarCropper';

import { avatarViewerModalConfig } from './config';
import AvatarViewerBody from './parts/AvatarViewerBody';
import type { IProps } from './types';
import { useAvatarViewerModal } from './useAvatarViewerModal';

import './style.less';

const AvatarViewerModal: FC<IProps> = ({
  open,
  onClose,
  displayAvatar,
  displayName,
}) => {
  const {
    cropSrc,
    uploading,
    avatarBusy,
    beforeUpload,
    handleCropConfirm,
    handleCropCancel,
  } = useAvatarViewerModal(onClose);

  return (
    <>
      <Modal
        title={avatarViewerModalConfig.title}
        open={open}
        onCancel={onClose}
        footer={null}
        width={avatarViewerModalConfig.width}
        destroyOnHidden
        className="avatar-viewer-modal"
      >
        <AvatarViewerBody
          displayAvatar={displayAvatar}
          displayName={displayName}
          avatarBusy={avatarBusy}
          uploading={uploading}
          beforeUpload={beforeUpload}
        />
      </Modal>

      {cropSrc && (
        <AvatarCropper
          open={Boolean(cropSrc)}
          imageSrc={cropSrc}
          onCancel={handleCropCancel}
          onConfirm={handleCropConfirm}
        />
      )}
    </>
  );
};

export default memo(AvatarViewerModal);
