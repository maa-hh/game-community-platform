import React, { memo } from 'react';
import type { FC } from 'react';
import { Modal } from 'antd';

import CoverCropperBody from './parts/CoverCropperBody';
import { coverCropperConfig } from './config';
import type { ICoverCropperModalProps } from './types';
import { useCoverCropper } from './useCoverCropper';

import './style.less';

const CoverCropperModal: FC<ICoverCropperModalProps> = ({
  open,
  imageSrc,
  onCancel,
  onConfirm,
}) => {
  const {
    crop,
    aspectKey,
    aspect,
    imgRef,
    confirming,
    onCropChange,
    onCropComplete,
    onAspectKeyChange,
    onImageLoad,
    handleOk,
  } = useCoverCropper(imageSrc, onConfirm);

  return (
    <Modal
      title={coverCropperConfig.title}
      open={open}
      onCancel={onCancel}
      onOk={handleOk}
      okText={coverCropperConfig.okText}
      cancelText={coverCropperConfig.cancelText}
      confirmLoading={confirming}
      destroyOnHidden
      width={coverCropperConfig.width}
      className="cover-cropper-modal"
    >
      <CoverCropperBody
        imageSrc={imageSrc}
        crop={crop}
        aspectKey={aspectKey}
        aspect={aspect}
        imgRef={imgRef}
        onCropChange={onCropChange}
        onCropComplete={onCropComplete}
        onAspectKeyChange={onAspectKeyChange}
        onImageLoad={onImageLoad}
      />
    </Modal>
  );
};

export default memo(CoverCropperModal);
