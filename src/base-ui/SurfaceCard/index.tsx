import React, { memo } from 'react';
import type { FC, HTMLAttributes, ReactNode } from 'react';

import './style.less';

export interface SurfaceCardProps extends HTMLAttributes<HTMLDivElement> {
  children?: ReactNode;
  /** 内边距，默认 true */
  padded?: boolean;
  /** 紧凑底边距（用于列表中连续卡片） */
  flush?: boolean;
  as?: 'div' | 'section' | 'article';
}

/** 统一内容白底盒：圆角 + 边框 + 背景（详情卡 / 编辑器 / 评论区外壳） */
const SurfaceCard: FC<SurfaceCardProps> = ({
  children,
  padded = true,
  flush,
  as: Tag = 'div',
  className,
  ...rest
}) => {
  const cls = [
    'surface-card',
    padded ? 'is-padded' : '',
    flush ? 'is-flush' : '',
    className || '',
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <Tag className={cls} {...rest}>
      {children}
    </Tag>
  );
};

export default memo(SurfaceCard);
