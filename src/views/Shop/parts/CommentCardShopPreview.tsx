import React, { memo, useMemo } from 'react';
import type { CSSProperties, FC } from 'react';

import { resolveCommentCardAsset } from '@/constants/commentCardCatalog';
import { commentCardStyle, commentCardTextTheme } from '@/utils/cosmeticAsset';

import './CommentCardShopPreview.less';

export interface CommentCardShopPreviewProps {
  cosmeticCode: string;
  assetJson?: unknown;
  className?: string;
}

const CommentCardShopPreview: FC<CommentCardShopPreviewProps> = ({
  cosmeticCode,
  assetJson,
  className,
}) => {
  const asset = useMemo(
    () => resolveCommentCardAsset(cosmeticCode, assetJson),
    [cosmeticCode, assetJson],
  );
  const cardStyle = useMemo(
    () => commentCardStyle(asset ? JSON.stringify(asset) : assetJson),
    [asset, assetJson],
  );
  const textTheme = commentCardTextTheme(
    asset ? JSON.stringify(asset) : assetJson,
  );

  if (!cardStyle) return null;

  return (
    <div
      className={[
        'comment-card-shop-preview',
        textTheme ? `comment-card-shop-preview--text-${textTheme}` : '',
        className,
      ]
        .filter(Boolean)
        .join(' ')}
      style={cardStyle as CSSProperties}
      aria-hidden
    >
      <span className="comment-card-shop-preview__name">玩家昵称</span>
      <p className="comment-card-shop-preview__content">这是一条评论预览效果</p>
    </div>
  );
};

export default memo(CommentCardShopPreview);
