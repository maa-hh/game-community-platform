import React from 'react';
import type { FC } from 'react';
import { Segmented, Typography } from 'antd';
import ReactCrop from 'react-image-crop';

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
}) => (
  <div className="cover-cropper">
    <Segmented
      className="cover-cropper__aspect"
      options={coverCropAspectOptions.map((item) => ({
        label: item.label,
        value: item.value,
      }))}
      value={aspectKey}
      onChange={(value) => onAspectKeyChange(value as typeof aspectKey)}
    />

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

    <Text type="secondary" className="cover-cropper__hint">
      {coverCropperConfig.hint}
    </Text>
  </div>
);

export default CoverCropperBody;
