import React, { memo } from 'react';
import type { FC } from 'react';

import TextCoverPoster from '@/base-ui/TextCoverPoster';
import VideoCover from '@/base-ui/VideoCover';
import MediaCover from '@/base-ui/MediaCover';

interface ShareCardCoverProps {
  isVideo: boolean;
  cover?: string;
  videoUrl?: string;
  posterTitle?: string;
}

const ShareCardCover: FC<ShareCardCoverProps> = ({
  isVideo,
  cover,
  videoUrl,
  posterTitle,
}) => {
  if (isVideo) {
    return <VideoCover coverUrl={cover} videoUrl={videoUrl} />;
  }
  if (cover) {
    return <MediaCover src={cover} type="image" />;
  }
  if (posterTitle?.trim()) {
    return <TextCoverPoster title={posterTitle} />;
  }
  return null;
};

export default memo(ShareCardCover);
