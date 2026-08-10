import React, { memo, useMemo } from 'react';
import type { FC } from 'react';

import type { IGamePrice } from '@/types/game';
import { resolveGamePriceDisplay } from '@/utils/formatGamePrice';

import './style.less';

export interface GamePriceTagProps {
  price?: IGamePrice;
  size?: 'md' | 'sm';
  /** 展示促销截止信息（介绍区用） */
  showPromo?: boolean;
  className?: string;
}

const GamePriceTag: FC<GamePriceTagProps> = ({
  price,
  size = 'md',
  showPromo = false,
  className,
}) => {
  const display = useMemo(() => resolveGamePriceDisplay(price), [price]);
  if (!display?.currentPriceText) return null;

  const showDeadline =
    showPromo &&
    display.hasDiscount &&
    (display.discountEndDateText || display.discountDaysLeftText);

  const rootClass = [
    'game-price-tag',
    `game-price-tag--${size}`,
    display.hasDiscount ? 'game-price-tag--discounted' : '',
    display.isFree ? 'game-price-tag--free' : '',
    showDeadline ? 'game-price-tag--with-promo' : '',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  const priceBody =
    display.hasDiscount && display.discountPercent != null ? (
      <>
        <span className="game-price-tag__discount">
          -{display.discountPercent}%
        </span>
        <span className="game-price-tag__prices">
          {display.originalPriceText ? (
            <span className="game-price-tag__original">
              {display.originalPriceText}
            </span>
          ) : null}
          <span className="game-price-tag__current">
            {display.currentPriceText}
          </span>
        </span>
      </>
    ) : (
      <span className="game-price-tag__current">
        {display.currentPriceText}
      </span>
    );

  return (
    <span className={rootClass}>
      <span className="game-price-tag__main">{priceBody}</span>
      {showDeadline ? (
        <span className="game-price-tag__promo">
          <span className="game-price-tag__promo-label">特别促销</span>
          {display.discountEndDateText ? (
            <span className="game-price-tag__promo-date">
              {display.discountEndDateText}
            </span>
          ) : null}
          {display.discountDaysLeftText ? (
            <span className="game-price-tag__promo-days">
              {display.discountDaysLeftText}
            </span>
          ) : null}
        </span>
      ) : null}
    </span>
  );
};

export default memo(GamePriceTag);
