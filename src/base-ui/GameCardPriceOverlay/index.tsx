import React, { memo, useMemo } from 'react';
import type { FC } from 'react';

import type { IGamePrice } from '@/types/game';
import { resolveGamePriceDisplay } from '@/utils/formatGamePrice';

import './style.less';

export interface GameCardPriceOverlayProps {
  price?: IGamePrice;
  className?: string;
}

function resolveShortDaysLeft(daysLeftText?: string | null): string | null {
  if (!daysLeftText) return null;
  if (daysLeftText === '今天截止') return '今天截止';
  if (daysLeftText === '还有 1 天截止') return '剩 1 天';
  const match = daysLeftText.match(/还有\s*(\d+)\s*天截止/);
  if (match) return `剩 ${match[1]} 天`;
  return daysLeftText;
}

const GameCardPriceOverlay: FC<GameCardPriceOverlayProps> = ({
  price,
  className,
}) => {
  const display = useMemo(() => resolveGamePriceDisplay(price), [price]);
  const daysShort = useMemo(
    () => resolveShortDaysLeft(display?.discountDaysLeftText),
    [display?.discountDaysLeftText],
  );

  if (!display?.currentPriceText) return null;

  const rootClass = [
    'game-card-price-overlay',
    display.hasDiscount ? 'game-card-price-overlay--discounted' : '',
    display.isFree ? 'game-card-price-overlay--free' : '',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <div className={rootClass}>
      {display.hasDiscount && display.discountPercent != null ? (
        <span className="game-card-price-overlay__discount">
          -{display.discountPercent}%
        </span>
      ) : null}
      <div className="game-card-price-overlay__prices">
        {display.originalPriceText ? (
          <span className="game-card-price-overlay__original">
            {display.originalPriceText}
          </span>
        ) : null}
        <span className="game-card-price-overlay__current">
          {display.currentPriceText}
        </span>
      </div>
      {daysShort ? (
        <span className="game-card-price-overlay__days">{daysShort}</span>
      ) : null}
    </div>
  );
};

export default memo(GameCardPriceOverlay);
