import type { CSSProperties } from 'react';

import type {
  IAvatarFrameAsset,
  ICommentCardAsset,
  ICommentFontAsset,
  IPostCardAsset,
  IProfileBgAsset,
} from '@/types/cosmetic';
import { isDarkColor } from '@/utils/colorLuminance';

function parseJson<T>(raw?: string): T | undefined {
  if (!raw?.trim()) return undefined;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return undefined;
  }
}

/** 兼容后端 assetJson 为字符串或已解析对象 */
export function normalizeAssetJson(raw?: unknown): string | undefined {
  if (raw == null) return undefined;
  if (typeof raw === 'string') {
    const trimmed = raw.trim();
    return trimmed || undefined;
  }
  if (typeof raw === 'object') {
    try {
      return JSON.stringify(raw);
    } catch {
      return undefined;
    }
  }
  return undefined;
}

function parseAssetJson<T>(raw?: unknown): T | undefined {
  return parseJson<T>(normalizeAssetJson(raw));
}

export function parseAvatarFrameAsset(raw?: unknown) {
  return parseAssetJson<IAvatarFrameAsset>(raw);
}

export function parseCommentCardAsset(raw?: unknown) {
  return parseAssetJson<ICommentCardAsset>(raw);
}

export function parseProfileBgAsset(raw?: unknown) {
  return parseAssetJson<IProfileBgAsset>(raw);
}

export function parsePostCardAsset(raw?: unknown) {
  return parseAssetJson<IPostCardAsset>(raw);
}

export function parseCommentFontAsset(raw?: unknown) {
  return parseAssetJson<ICommentFontAsset>(raw);
}

export function avatarFrameUrl(raw?: unknown) {
  return parseAvatarFrameAsset(raw)?.frameUrl;
}

export function avatarFrameAsset(raw?: unknown) {
  return parseAvatarFrameAsset(raw);
}

export function commentCardStyle(raw?: unknown): CSSProperties | undefined {
  const asset = parseCommentCardAsset(raw);
  if (!asset) return undefined;
  return {
    background: asset.bg,
    border: asset.border,
    borderRadius: asset.radius,
    boxShadow: asset.shadow,
  };
}

export function commentCardTextTheme(
  raw?: unknown,
): ICommentCardAsset['textTheme'] | undefined {
  const asset = parseCommentCardAsset(raw);
  if (!asset?.bg) return undefined;
  if (asset.textTheme) return asset.textTheme;
  return isDarkColor(asset.bg) ? 'light' : 'dark';
}

export function commentFontStyle(raw?: unknown): CSSProperties | undefined {
  const asset = parseCommentFontAsset(raw);
  if (!asset) return undefined;
  return {
    color: asset.color,
    fontFamily: asset.fontFamily,
    fontWeight: asset.fontWeight,
  };
}

export function postCardStyle(raw?: unknown): CSSProperties | undefined {
  const asset = parsePostCardAsset(raw);
  if (!asset) return undefined;
  return {
    border: asset.border,
    borderRadius: asset.borderRadius,
    background: asset.background,
  };
}

export function profileHeroStyle(raw?: unknown): CSSProperties | undefined {
  const asset = parseProfileBgAsset(raw);
  if (!asset?.bgImage) return undefined;
  const gradient = asset.overlayGradient
    ? `linear-gradient(${asset.overlayGradient})`
    : `linear-gradient(${asset.overlay || 'rgba(0,0,0,0.15)'}, ${asset.overlay || 'rgba(0,0,0,0.15)'})`;
  return {
    backgroundImage: `${gradient}, url(${asset.bgImage})`,
    backgroundSize: '100% 100%, cover',
    backgroundRepeat: 'no-repeat, no-repeat',
    backgroundPosition: 'center top, center top',
  };
}

export function profileBgTextTheme(
  raw?: unknown,
): IProfileBgAsset['textTheme'] | undefined {
  return parseProfileBgAsset(raw)?.textTheme;
}
