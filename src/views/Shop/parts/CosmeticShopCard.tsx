import React, { memo } from 'react';
import type { FC, ReactNode } from 'react';
import { Button, Card, Space, Tag, Typography } from 'antd';

import DecoratedAvatar from '@/base-ui/DecoratedAvatar';
import CommentCardShopPreview from '@/views/Shop/parts/CommentCardShopPreview';
import ProfileBgShopPreview from '@/views/Shop/parts/ProfileBgShopPreview';
import {
  isAvatarFrameCosmeticCode,
  resolveAvatarFrameAsset,
} from '@/constants/avatarFrameCatalog';
import {
  isCommentCardCosmeticCode,
  resolveCommentCardAsset,
} from '@/constants/commentCardCatalog';
import {
  isProfileBgCosmeticCode,
  resolveProfileBgAsset,
} from '@/constants/profileBgCatalog';
import type { CosmeticItemState, CosmeticSlot } from '@/types/cosmetic';
import type { RepurchasePolicy } from '@/service/shop';

import './CosmeticShopCard.less';

export interface CosmeticShopCardProps {
  cosmeticCode: string;
  name: string;
  description?: string;
  pricePoints?: number;
  stock?: number;
  repurchasePolicy?: RepurchasePolicy;
  limitCount?: number;
  limitWindowSeconds?: number;
  icon?: string;
  slot?: CosmeticSlot;
  owned?: boolean;
  equipped?: boolean;
  state?: CosmeticItemState;
  canBuy?: boolean;
  cannotBuyReason?: string;
  assetJson?: unknown;
  previewName?: string;
  previewAvatar?: string;
  loading?: boolean;
  actionLabel?: string;
  actionDisabled?: boolean;
  onAction?: () => void;
  extraActions?: ReactNode;
}

function resolveSlotLabel(
  cosmeticCode: string,
  slot?: CosmeticSlot,
): string | undefined {
  if (isProfileBgCosmeticCode(cosmeticCode)) return '主页背景';
  if (isAvatarFrameCosmeticCode(cosmeticCode)) return '头像挂件';
  if (isCommentCardCosmeticCode(cosmeticCode)) return '评论卡片';
  if (slot === 'COMMENT_FONT') return '评论字体';
  if (slot === 'POST_CARD') return '帖子卡片';
  return undefined;
}

const CosmeticShopCard: FC<CosmeticShopCardProps> = ({
  cosmeticCode,
  name,
  description,
  pricePoints,
  stock,
  repurchasePolicy,
  limitCount,
  limitWindowSeconds,
  icon,
  slot,
  owned,
  equipped,
  state,
  canBuy,
  cannotBuyReason,
  assetJson,
  previewName,
  previewAvatar,
  loading,
  actionLabel,
  actionDisabled,
  onAction,
  extraActions,
}) => {
  const isProfileBg = isProfileBgCosmeticCode(cosmeticCode);
  const isAvatarFrame = isAvatarFrameCosmeticCode(cosmeticCode);
  const isCommentCard = isCommentCardCosmeticCode(cosmeticCode);
  const profileBgAsset = resolveProfileBgAsset(cosmeticCode, assetJson, icon);
  const profileBgAssetJson = profileBgAsset
    ? JSON.stringify(profileBgAsset)
    : assetJson;
  const frameAsset = resolveAvatarFrameAsset(cosmeticCode, assetJson);
  const commentCardAsset = resolveCommentCardAsset(cosmeticCode, assetJson);
  const hasCommentCardPreview = Boolean(
    commentCardAsset?.bg || commentCardAsset?.border,
  );
  const slotLabel = resolveSlotLabel(cosmeticCode, slot);

  const defaultActionLabel = equipped
    ? '已装备'
    : owned
      ? isProfileBg
        ? '装备到主页'
        : isAvatarFrame
          ? '装备挂件'
          : '装备'
      : canBuy === false
        ? cannotBuyReason || '不可兑换'
        : '立即兑换';

  const cover =
    isProfileBg && profileBgAsset?.bgImage ? (
      <div className="cosmetic-shop-card__cover cosmetic-shop-card__cover--profile-bg">
        <ProfileBgShopPreview
          cosmeticCode={cosmeticCode}
          assetJson={profileBgAssetJson}
          title={name}
        />
      </div>
    ) : isAvatarFrame ? (
      <div className="cosmetic-shop-card__cover cosmetic-shop-card__cover--frame">
        <div className="cosmetic-shop-card__frame-stage">
          <DecoratedAvatar
            name={previewName || '预览'}
            src={previewAvatar}
            size={52}
            frameUrl={frameAsset?.frameUrl}
            frameScale={frameAsset?.scale}
            avatarRatio={frameAsset?.avatarRatio}
          />
        </div>
      </div>
    ) : isCommentCard && hasCommentCardPreview ? (
      <div className="cosmetic-shop-card__cover cosmetic-shop-card__cover--comment-card">
        <CommentCardShopPreview
          cosmeticCode={cosmeticCode}
          assetJson={assetJson}
        />
      </div>
    ) : icon ? (
      <div className="cosmetic-shop-card__cover cosmetic-shop-card__cover--icon">
        <img src={icon} alt={name} className="cosmetic-shop-card__icon" />
      </div>
    ) : (
      <div className="cosmetic-shop-card__cover cosmetic-shop-card__cover--placeholder" />
    );

  return (
    <Card
      className={[
        'cosmetic-shop-card',
        isProfileBg ? 'cosmetic-shop-card--profile-bg' : '',
        isAvatarFrame ? 'cosmetic-shop-card--avatar-frame' : '',
        isCommentCard ? 'cosmetic-shop-card--comment-card' : '',
        equipped ? 'cosmetic-shop-card--equipped' : '',
      ]
        .filter(Boolean)
        .join(' ')}
      cover={cover}
    >
      <Typography.Title level={5} className="cosmetic-shop-card__title">
        {name}
      </Typography.Title>
      {description ? (
        <Typography.Paragraph
          type="secondary"
          className="cosmetic-shop-card__desc"
          ellipsis={{ rows: 2 }}
        >
          {description}
        </Typography.Paragraph>
      ) : null}
      <Space wrap className="cosmetic-shop-card__tags">
        {slotLabel ? <Tag color="purple">{slotLabel}</Tag> : null}
        {pricePoints != null ? (
          <Tag color="volcano">{pricePoints} 积分</Tag>
        ) : null}
        {stock != null && stock >= 0 ? (
          <Tag color={stock === 0 ? 'red' : 'gold'}>
            {stock === 0 ? '已售罄' : `剩余 ${stock}`}
          </Tag>
        ) : null}
        {repurchasePolicy === 'ONCE_FOREVER' ? (
          <Tag>每人限购 1 件</Tag>
        ) : repurchasePolicy === 'LIMIT_PER_WINDOW' && limitCount != null ? (
          <Tag>
            {limitWindowSeconds
              ? `${limitWindowSeconds >= 86400 ? '每日' : '窗口内'}限购 ${limitCount} 件`
              : `限购 ${limitCount} 件`}
          </Tag>
        ) : repurchasePolicy === 'COOLDOWN' && limitWindowSeconds ? (
          <Tag>冷却 {limitWindowSeconds} 秒</Tag>
        ) : null}
        {owned ? <Tag color="green">已拥有</Tag> : null}
        {equipped ? <Tag color="blue">已装备</Tag> : null}
        {state === 'EXPIRED' ? <Tag color="red">已过期</Tag> : null}
      </Space>
      {onAction ? (
        <Button
          block
          type="primary"
          className="cosmetic-shop-card__action"
          loading={loading}
          disabled={actionDisabled}
          onClick={onAction}
        >
          {actionLabel || defaultActionLabel}
        </Button>
      ) : null}
      {extraActions ? (
        <div className="cosmetic-shop-card__extra">{extraActions}</div>
      ) : null}
    </Card>
  );
};

export default memo(CosmeticShopCard);
