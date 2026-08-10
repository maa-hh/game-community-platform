import React, { memo } from 'react';
import type { FC } from 'react';

import DecoratedAvatar from '@/base-ui/DecoratedAvatar';
import { resolveAvatarFrameAsset } from '@/constants/avatarFrameCatalog';

import './AvatarFrameShopCard.less';

export interface AvatarFrameShopCardProps {
  cosmeticCode: string;
  name: string;
  previewName: string;
  previewAvatar?: string;
  assetJson?: unknown;
  pricePoints?: number;
  owned?: boolean;
  equipped?: boolean;
  selected?: boolean;
  onClick?: () => void;
}

const AvatarFrameShopCard: FC<AvatarFrameShopCardProps> = ({
  cosmeticCode,
  name,
  previewName,
  previewAvatar,
  assetJson,
  pricePoints,
  owned,
  equipped,
  selected,
  onClick,
}) => {
  const asset = resolveAvatarFrameAsset(cosmeticCode, assetJson);

  return (
    <button
      type="button"
      className={[
        'avatar-frame-shop-card',
        selected ? 'is-selected' : '',
        equipped ? 'is-equipped' : '',
      ]
        .filter(Boolean)
        .join(' ')}
      onClick={onClick}
    >
      <div className="avatar-frame-shop-card__preview">
        <DecoratedAvatar
          name={previewName}
          src={previewAvatar}
          size={52}
          frameUrl={asset?.frameUrl}
          frameScale={asset?.scale}
        />
      </div>
      <div className="avatar-frame-shop-card__name">{name}</div>
      {equipped ? (
        <span className="avatar-frame-shop-card__tag avatar-frame-shop-card__tag--equipped">
          已装备
        </span>
      ) : owned ? (
        <span className="avatar-frame-shop-card__tag avatar-frame-shop-card__tag--owned">
          已拥有
        </span>
      ) : pricePoints != null ? (
        <span className="avatar-frame-shop-card__tag">{pricePoints} 积分</span>
      ) : null}
    </button>
  );
};

export default memo(AvatarFrameShopCard);
