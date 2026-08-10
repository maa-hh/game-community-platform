import React, { memo } from 'react';
import type { FC, MouseEventHandler } from 'react';
import { PlayCircleFilled } from '@ant-design/icons';

import './style.less';

export interface MediaCoverProps {
  src: string;
  alt?: string;
  /** video 叠播放钮；image 纯封面 */
  type?: 'image' | 'video';
  className?: string;
  onClick?: MouseEventHandler<HTMLDivElement>;
}

/** 统一媒体封面：图片 / 视频封面+播放标识 */
const MediaCover: FC<MediaCoverProps> = ({
  src,
  alt = '',
  type = 'image',
  className,
  onClick,
}) => {
  return (
    <div
      className={`media-cover${type === 'video' ? ' is-video' : ''}${
        className ? ` ${className}` : ''
      }`}
      onClick={onClick}
      role={onClick ? 'button' : undefined}
      tabIndex={onClick ? 0 : undefined}
    >
      <img src={src} alt={alt} loading="lazy" />
      {type === 'video' && (
        <span className="media-cover__play" aria-hidden>
          <PlayCircleFilled />
        </span>
      )}
    </div>
  );
};

export default memo(MediaCover);
