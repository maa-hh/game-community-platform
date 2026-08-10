import React, { memo } from 'react';
import type { FC } from 'react';
import { PlayCircleFilled } from '@ant-design/icons';
import { Spin } from 'antd';

import MediaCover from '@/base-ui/MediaCover';
import { useVideoPoster } from '@/hooks/useVideoPoster';

import './style.less';

export interface VideoCoverProps {
  coverUrl?: string;
  videoUrl?: string;
  className?: string;
}

/** 视频封面：有 cover 用 cover，否则截取视频首帧 */
const VideoCover: FC<VideoCoverProps> = ({ coverUrl, videoUrl, className }) => {
  const { poster, loading } = useVideoPoster(videoUrl, coverUrl);

  if (poster) {
    return <MediaCover src={poster} type="video" className={className} />;
  }

  if (loading && videoUrl) {
    return (
      <div
        className={`video-cover video-cover--loading${className ? ` ${className}` : ''}`}
      >
        <Spin size="small" />
      </div>
    );
  }

  if (videoUrl) {
    return (
      <div
        className={`video-cover video-cover--fallback${className ? ` ${className}` : ''}`}
      >
        <PlayCircleFilled aria-hidden />
      </div>
    );
  }

  return null;
};

export default memo(VideoCover);
