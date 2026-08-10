import React, { memo } from 'react';
import type { FC } from 'react';
import { Button } from 'antd';
import { LinkOutlined } from '@ant-design/icons';

import GamePriceTag from '@/base-ui/GamePriceTag';
import type { IGameDetail } from '@/types/game';
import { resolveGamePriceDisplay } from '@/utils/formatGamePrice';

import './GameStoreBar.less';

export interface GameStoreBarProps {
  detail: IGameDetail;
  className?: string;
}

const GameStoreBar: FC<GameStoreBarProps> = ({ detail, className }) => {
  const hasPrice = Boolean(resolveGamePriceDisplay(detail.price));
  if (!hasPrice && !detail.steamUrl) {
    return null;
  }

  return (
    <div className={`game-store-bar${className ? ` ${className}` : ''}`}>
      {hasPrice ? (
        <div className="game-store-bar__price-wrap">
          <span className="game-store-bar__price-label">Steam 售价</span>
          <GamePriceTag price={detail.price} showPromo />
        </div>
      ) : (
        <span />
      )}
      {detail.steamUrl ? (
        <Button
          type="link"
          icon={<LinkOutlined />}
          href={detail.steamUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="game-store-bar__link"
        >
          在 Steam 查看
        </Button>
      ) : null}
    </div>
  );
};

export default memo(GameStoreBar);
