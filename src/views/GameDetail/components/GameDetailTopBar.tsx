import React, { memo } from 'react';
import type { FC } from 'react';
import { Button } from 'antd';
import {
  ArrowLeftOutlined,
  LinkOutlined,
  ShareAltOutlined,
} from '@ant-design/icons';

import GamePriceTag from '@/base-ui/GamePriceTag';
import type { IGamePrice } from '@/types/game';
import { resolveGamePriceDisplay } from '@/utils/formatGamePrice';

import './GameDetailTopBar.less';

export interface GameDetailTopBarProps {
  name?: string;
  price?: IGamePrice;
  steamUrl?: string;
  onBack: () => void;
  onShare?: () => void;
}

const GameDetailTopBar: FC<GameDetailTopBarProps> = ({
  name,
  price,
  steamUrl,
  onBack,
  onShare,
}) => {
  const hasPrice = Boolean(resolveGamePriceDisplay(price));

  return (
    <header className="game-detail-top-bar">
      <Button
        type="text"
        className="game-detail-top-bar__back"
        icon={<ArrowLeftOutlined />}
        aria-label="返回"
        onMouseDown={(event) => event.preventDefault()}
        onClick={onBack}
      />

      {name ? (
        <div className="game-detail-top-bar__meta">
          <span className="game-detail-top-bar__name" title={name}>
            {name}
          </span>
        </div>
      ) : null}

      <div className="game-detail-top-bar__actions">
        {hasPrice ? (
          <GamePriceTag
            price={price}
            size="sm"
            className="game-detail-top-bar__price"
          />
        ) : null}
        {steamUrl ? (
          <Button
            type="link"
            size="small"
            icon={<LinkOutlined />}
            href={steamUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="game-detail-top-bar__steam"
          >
            在 Steam 查看
          </Button>
        ) : null}
        {onShare ? (
          <Button
            type="text"
            size="small"
            icon={<ShareAltOutlined />}
            aria-label="分享"
            className="game-detail-top-bar__share"
            onClick={onShare}
          />
        ) : null}
      </div>
    </header>
  );
};

export default memo(GameDetailTopBar);
