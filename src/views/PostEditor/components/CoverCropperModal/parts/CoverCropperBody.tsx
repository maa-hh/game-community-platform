import React from 'react';
import type { FC } from 'react';
import { Button, Segmented, Typography } from 'antd';
import { RotateRightOutlined } from '@ant-design/icons';
import ReactCrop from 'react-image-crop';

import ImageZoomControl from '@/base-ui/ImageZoomControl';

import { coverCropAspectOptions, coverCropperConfig } from '../config';
import type { CoverCropperBodyProps } from '../types';

import 'react-image-crop/dist/ReactCrop.css';

const { Text } = Typography;

const CoverCropperBody: FC<CoverCropperBodyProps> = ({
  imageSrc,
  crop,
  aspectKey,
  aspect,
  imgRef,
  onCropChange,
  onCropComplete,
  onAspectKeyChange,
  onImageLoad,
  zoom,
  onZoomChange,
  rotation,
  rotating,
  onRotate,
}) => (
  <div className="cover-cropper">
    <div className="cover-cropper__toolbar">
      <Segmented
        className="cover-cropper__aspect"
        options={coverCropAspectOptions.map((item) => ({
          label: item.label,
          value: item.value,
        }))}
        value={aspectKey}
        onChange={(value) => onAspectKeyChange(value as typeof aspectKey)}
      />
      <Button
        icon={<RotateRightOutlined />}
        loading={rotating}
        onClick={onRotate}
      >
        {coverCropperConfig.rotateText}（{rotation}°）
      </Button>
    </div>

    <div className="cover-cropper__stage">
      <ReactCrop
        crop={crop}
        aspect={aspect}
        ruleOfThirds
        keepSelection
        onChange={onCropChange}
        onComplete={onCropComplete}
      >
        <img
          ref={imgRef}
          src={imageSrc}
          alt=""
          className="cover-cropper__image"
          onLoad={onImageLoad}
        />
      </ReactCrop>
    </div>

    <ImageZoomControl
      className="cover-cropper__zoom"
      label={coverCropperConfig.zoomLabel}
      min={coverCropperConfig.zoomMin}
      max={coverCropperConfig.zoomMax}
      step={coverCropperConfig.zoomStep}
      value={zoom}
      onChange={onZoomChange}
    />

    <Text type="secondary" className="cover-cropper__hint">
      {coverCropperConfig.hint}
    </Text>
  </div>
);

export default CoverCropperBody;
