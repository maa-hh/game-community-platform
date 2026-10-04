import React, { memo } from 'react';
import type { FC } from 'react';

import ShareCard from '@/components/ShareCard';
import ManualCopyField from '@/base-ui/ManualCopyField';
import type { PostRefCard } from '@/types/post';

interface ShareLinkPreviewProps {
  card: PostRefCard;
  copyText: string;
  manualCopyText?: string | null;
}

/** 外链分享预览：与站内转发 ShareCard 同款 */
const ShareLinkPreview: FC<ShareLinkPreviewProps> = ({
  card,
  copyText,
  manualCopyText,
}) => (
  <div className="share-sheet__preview">
    <ShareCard data={card} preview className="share-sheet__card" />
    <div className="share-sheet__copy-block">
      <div className="share-sheet__copy-label">复制内容预览</div>
      <pre className="share-sheet__copy-text">{copyText}</pre>
    </div>
    <p className="share-sheet__preview-tip">
      复制后粘贴分享，对方点击链接进入帖子详情
    </p>
    {manualCopyText ? <ManualCopyField text={manualCopyText} /> : null}
  </div>
);

export default memo(ShareLinkPreview);
