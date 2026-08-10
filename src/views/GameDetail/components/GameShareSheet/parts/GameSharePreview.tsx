import React, { memo } from 'react';
import type { FC } from 'react';

import type { IGameDetail } from '@/types/game';

interface GameSharePreviewProps {
  detail: IGameDetail;
  priceText?: string | null;
  copyText: string;
}

const GameSharePreview: FC<GameSharePreviewProps> = ({
  detail,
  priceText,
  copyText,
}) => (
  <div className="share-sheet__preview game-share-sheet__preview">
    <article className="game-share-sheet__card">
      {detail.coverUrl ? (
        <img
          src={detail.coverUrl}
          alt={detail.name}
          className="game-share-sheet__cover"
        />
      ) : (
        <div className="game-share-sheet__cover game-share-sheet__cover--placeholder">
          {detail.name.slice(0, 1)}
        </div>
      )}
      <div className="game-share-sheet__body">
        <h3 className="game-share-sheet__name">{detail.name}</h3>
        {priceText ? (
          <span className="game-share-sheet__price">{priceText}</span>
        ) : null}
      </div>
    </article>
    <div className="share-sheet__copy-block">
      <div className="share-sheet__copy-label">复制游戏详情链接预览</div>
      <pre className="share-sheet__copy-text">{copyText}</pre>
    </div>
    <p className="share-sheet__preview-tip">
      复制后粘贴分享，对方点击链接进入游戏详情
    </p>
  </div>
);

export default memo(GameSharePreview);
