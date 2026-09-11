import React from 'react';
import type { ComponentType, FC } from 'react';
import ReactEasyCrop from 'react-easy-crop';
import { Typography } from 'antd';

import ImageZoomControl from '@/base-ui/ImageZoomControl';

import { avatarCropperConfig } from '../config';
import type { EasyCropperProps } from '../types';

const { Text } = Typography;

/**
 * react-easy-crop@6 的 types 使用 `export type *`（需 TS 5+），
 * 本项目仍为 TS 4.9，故用运行时默认导出 + 本地 props 类型兜底。
 */
const Cropper = ReactEasyCrop as unknown as ComponentType<EasyCropperProps>;

interface IProps {
  imageSrc: string;
  crop: { x: number; y: number };
  zoom: number;
  onCropChange: (location: { x: number; y: number }) => void;
  onZoomChange: (zoom: number) => void;
  onCropComplete: EasyCropperProps['onCropComplete'];
}

const AvatarCropperBody: FC<IProps> = ({
  imageSrc,
  crop,
  zoom,
  onCropChange,
  onZoomChange,
  onCropComplete,
}) => {
  return (
    <div className="avatar-cropper">
      <div className="avatar-cropper__stage">
        <Cropper
          image={imageSrc}
          crop={crop}
          zoom={zoom}
          minZoom={avatarCropperConfig.zoomMin}
          maxZoom={avatarCropperConfig.zoomMax}
          aspect={avatarCropperConfig.aspect}
          cropShape={avatarCropperConfig.cropShape}
          showGrid={avatarCropperConfig.showGrid}
          onCropChange={onCropChange}
          onZoomChange={onZoomChange}
          onCropComplete={onCropComplete}
        />
      </div>
      <ImageZoomControl
        className="avatar-cropper__zoom"
        label={avatarCropperConfig.zoomLabel}
        min={avatarCropperConfig.zoomMin}
        max={avatarCropperConfig.zoomMax}
        step={avatarCropperConfig.zoomStep}
        value={zoom}
        onChange={onZoomChange}
      />
      <Text type="secondary" className="avatar-cropper__hint">
        {avatarCropperConfig.hint}
      </Text>
    </div>
  );
};

export default AvatarCropperBody;
