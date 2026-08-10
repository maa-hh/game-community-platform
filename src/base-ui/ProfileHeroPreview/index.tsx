import React, { memo } from 'react';
import type { FC } from 'react';

import ProfileBgBackdrop from '@/base-ui/ProfileBgBackdrop';
import { resolveProfileBgAsset } from '@/constants/profileBgCatalog';
import { PROFILE_HERO_ASPECT_RATIO } from '@/constants/profileHeroLayout';

import './style.less';

export interface ProfileHeroPreviewProps {
  cosmeticCode?: string;
  assetJson?: unknown;
  name?: string;
  accountId?: string | number;
  bio?: string;
  className?: string;
}

const ProfileHeroPreview: FC<ProfileHeroPreviewProps> = ({
  cosmeticCode,
  assetJson,
  name = '个人主页预览',
  accountId = '10000',
  bio = '关注 · 粉丝 · 获赞',
  className,
}) => {
  const asset = resolveProfileBgAsset(cosmeticCode, assetJson);
  const textTheme = asset?.textTheme ?? 'light';

  return (
    <div
      className={[
        'profile-hero-preview',
        `profile-hero-preview--text-${textTheme}`,
        className,
      ]
        .filter(Boolean)
        .join(' ')}
      style={{ aspectRatio: String(PROFILE_HERO_ASPECT_RATIO) }}
      aria-hidden
    >
      <ProfileBgBackdrop
        className="profile-hero-preview__backdrop"
        cosmeticCode={cosmeticCode}
        assetJson={assetJson}
      />
      <div className="profile-hero-preview__inner">
        <div className="profile-hero-preview__lead">
          <span className="profile-hero-preview__avatar" />
        </div>
        <div className="profile-hero-preview__info">
          <div className="profile-hero-preview__name">{name}</div>
          <div className="profile-hero-preview__account-id">ID {accountId}</div>
          <div className="profile-hero-preview__bio">{bio}</div>
        </div>
      </div>
    </div>
  );
};

export default memo(ProfileHeroPreview);
