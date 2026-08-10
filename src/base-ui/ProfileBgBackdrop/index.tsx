import React, { memo, useMemo } from 'react';
import type { CSSProperties, FC } from 'react';

import { resolveProfileBgAsset } from '@/constants/profileBgCatalog';
import { normalizeAssetJson, parseProfileBgAsset } from '@/utils/cosmeticAsset';

import './style.less';

export interface ProfileBgBackdropProps {
  assetJson?: unknown;
  cosmeticCode?: string;
  className?: string;
}

function buildOverlayStyle(assetJson?: unknown): CSSProperties | undefined {
  const asset = parseProfileBgAsset(assetJson);
  if (!asset) return undefined;
  if (asset.overlayGradient) {
    return { background: `linear-gradient(${asset.overlayGradient})` };
  }
  if (asset.overlay) {
    return { background: asset.overlay };
  }
  return { background: 'rgba(0, 0, 0, 0.15)' };
}

const ProfileBgBackdrop: FC<ProfileBgBackdropProps> = ({
  assetJson,
  cosmeticCode,
  className,
}) => {
  const asset = useMemo(
    () => resolveProfileBgAsset(cosmeticCode, assetJson),
    [cosmeticCode, assetJson],
  );
  const overlayStyle = useMemo(
    () =>
      buildOverlayStyle(
        asset ? JSON.stringify(asset) : normalizeAssetJson(assetJson),
      ),
    [asset, assetJson],
  );

  if (!asset?.bgImage) return null;

  const objectPosition = asset.objectPosition ?? 'center 32%';

  return (
    <div
      className={['profile-bg-backdrop', className].filter(Boolean).join(' ')}
      aria-hidden
      style={
        {
          '--profile-bg-object-position': objectPosition,
        } as CSSProperties
      }
    >
      <img
        className="profile-bg-backdrop__image"
        src={asset.bgImage}
        alt=""
        loading="lazy"
        decoding="async"
        style={{ objectPosition }}
      />
      <div className="profile-bg-backdrop__overlay" style={overlayStyle} />
    </div>
  );
};

export default memo(ProfileBgBackdrop);
