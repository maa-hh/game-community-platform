import React, { memo } from 'react';
import type { FC } from 'react';
import { Modal } from 'antd';

import { avatarCropperConfig } from './config';
import AvatarCropperBody from './parts/AvatarCropperBody';
import type { IProps } from './types';
import { useAvatarCropper } from './useAvatarCropper';

import './style.less';

const AvatarCropper: FC<IProps> = ({ open, imageSrc, onCancel, onConfirm }) => {
  const { crop, setCrop, zoom, setZoom, confirming, onCropComplete, handleOk } =
    useAvatarCropper(imageSrc, onConfirm);

  return (
    <Modal
      title={avatarCropperConfig.title}
      open={open}
      onCancel={onCancel}
      onOk={handleOk}
      okText={avatarCropperConfig.okText}
      cancelText={avatarCropperConfig.cancelText}
      confirmLoading={confirming}
      destroyOnHidden
      width={avatarCropperConfig.width}
      className="avatar-cropper-modal"
    >
      <AvatarCropperBody
        imageSrc={imageSrc}
        crop={crop}
        zoom={zoom}
        onCropChange={setCrop}
        onZoomChange={setZoom}
        onCropComplete={onCropComplete}
      />
    </Modal>
  );
};

export default memo(AvatarCropper);
