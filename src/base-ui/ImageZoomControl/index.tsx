import React, { memo } from 'react';
import type { FC } from 'react';
import { InputNumber, Slider, Typography } from 'antd';

import './style.less';

const { Text } = Typography;

export interface ImageZoomControlProps {
  label?: string;
  min: number;
  max: number;
  step: number;
  value: number;
  onChange: (value: number) => void;
  className?: string;
}

const formatZoomValue = (value?: number) => {
  if (value === undefined) return '';
  return `${value}x`;
};

const ImageZoomControl: FC<ImageZoomControlProps> = ({
  label = '缩放',
  min,
  max,
  step,
  value,
  onChange,
  className,
}) => {
  const rootClassName = ['image-zoom-control', className]
    .filter(Boolean)
    .join(' ');

  return (
    <div className={rootClassName}>
      <Text type="secondary" className="image-zoom-control__label">
        {label}
      </Text>
      <Slider
        className="image-zoom-control__slider"
        min={min}
        max={max}
        step={step}
        value={value}
        tooltip={{ formatter: formatZoomValue }}
        onChange={(nextValue) => {
          if (typeof nextValue === 'number') onChange(nextValue);
        }}
      />
      <InputNumber
        className="image-zoom-control__input"
        aria-label={`${label}倍率`}
        min={min}
        max={max}
        step={step}
        precision={2}
        value={value}
        suffix="x"
        onChange={(nextValue) => {
          if (typeof nextValue === 'number') onChange(nextValue);
        }}
      />
    </div>
  );
};

export default memo(ImageZoomControl);
