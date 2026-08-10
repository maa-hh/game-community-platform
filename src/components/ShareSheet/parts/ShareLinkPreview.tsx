import React, { memo } from 'react';
import type { FC } from 'react';

import ShareCard from '@/components/ShareCard';
import type { PostRefCard } from '@/types/post';

interface ShareLinkPreviewProps {
  card: PostRefCard;
  copyText: string;
}

/** 外链分享预览：与站内转发 ShareCard 同款 */
const ShareLinkPreview: FC<ShareLinkPreviewProps> = ({ card, copyText }) => (
  <div className="share-sheet__preview">
    <ShareCard data={card} preview className="share-sheet__card" />
    <div className="share-sheet__copy-block">
      <div className="share-sheet__copy-label">复制内容预览</div>
      <pre className="share-sheet__copy-text">{copyText}</pre>
    </div>
    <p className="share-sheet__preview-tip">
      复制后粘贴分享，对方点击链接进入帖子详情
    </p>
  </div>
);

export default memo(ShareLinkPreview);
