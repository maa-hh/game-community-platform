import React, { memo } from 'react';
import type { FC } from 'react';

import ProfileHeroPreview from '@/base-ui/ProfileHeroPreview';

import './ProfileBgShopPreview.less';

export interface ProfileBgShopPreviewProps {
  cosmeticCode: string;
  assetJson?: unknown;
  title?: string;
  className?: string;
}

const ProfileBgShopPreview: FC<ProfileBgShopPreviewProps> = ({
  cosmeticCode,
  assetJson,
  title,
  className,
}) => (
  <ProfileHeroPreview
    className={['profile-bg-shop-preview', className].filter(Boolean).join(' ')}
    cosmeticCode={cosmeticCode}
    assetJson={assetJson}
    name={title || '个人主页预览'}
    bio="关注 · 粉丝 · 获赞"
  />
);

export default memo(ProfileBgShopPreview);
