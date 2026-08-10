import type { IAvatarFrameAsset } from '@/types/cosmetic';
import { parseAvatarFrameAsset } from '@/utils/cosmeticAsset';

export interface IAvatarFrameCatalogItem {
  code: string;
  name: string;
  asset: IAvatarFrameAsset;
}

/** 头像挂件本地资源（与 public/cosmetic/avatar-frame 及 SQL 种子一致） */
export const AVATAR_FRAME_CATALOG: IAvatarFrameCatalogItem[] = [
  {
    code: 'avatar_frame_golden_ring',
    name: '鎏金戒环',
    asset: {
      frameUrl: '/cosmetic/avatar-frame/golden-ring.svg',
      scale: 1.48,
    },
  },
  {
    code: 'avatar_frame_royal_crown',
    name: '精灵王冠',
    asset: {
      frameUrl: '/cosmetic/avatar-frame/royal-crown.svg',
      scale: 1.5,
    },
  },
  {
    code: 'avatar_frame_naval_blue',
    name: '碧海舰徽',
    asset: {
      frameUrl: '/cosmetic/avatar-frame/naval-blue.svg',
      scale: 1.48,
    },
  },
  {
    code: 'avatar_frame_shadow_lotus',
    name: '幽莲夜冠',
    asset: {
      frameUrl: '/cosmetic/avatar-frame/shadow-lotus.svg',
      scale: 1.5,
    },
  },
  {
    code: 'avatar_frame_emerald_serpent',
    name: '翡翠蛇环',
    asset: {
      frameUrl: '/cosmetic/avatar-frame/emerald-serpent.svg',
      scale: 1.48,
    },
  },
  {
    code: 'avatar_frame_sky_wings',
    name: '苍穹翼徽',
    asset: {
      frameUrl: '/cosmetic/avatar-frame/sky-wings.svg',
      scale: 1.52,
    },
  },
  {
    code: 'avatar_frame_amber_leaf',
    name: '琥珀叶环',
    asset: {
      frameUrl: '/cosmetic/avatar-frame/amber-leaf.svg',
      scale: 1.48,
    },
  },
  {
    code: 'avatar_frame_blaze_lion',
    name: '炽焰狮心',
    asset: {
      frameUrl: '/cosmetic/avatar-frame/blaze-lion.svg',
      scale: 1.5,
    },
  },
  {
    code: 'avatar_frame_steel_glory',
    name: '钢铁荣耀',
    asset: {
      frameUrl: '/cosmetic/avatar-frame/steel-glory.svg',
      scale: 1.48,
    },
  },
];

const AVATAR_FRAME_ASSET_MAP = new Map(
  AVATAR_FRAME_CATALOG.map((item) => [item.code, item.asset]),
);

export function isAvatarFrameCosmeticCode(code?: string) {
  return Boolean(code?.startsWith('avatar_frame_'));
}

export function resolveAvatarFrameAsset(
  cosmeticCode?: string,
  assetJson?: unknown,
): IAvatarFrameAsset | undefined {
  const parsed = parseAvatarFrameAsset(assetJson);
  if (parsed?.frameUrl) return parsed;
  if (!cosmeticCode) return undefined;
  return AVATAR_FRAME_ASSET_MAP.get(cosmeticCode);
}

export function resolveAvatarFrameUrl(
  cosmeticCode?: string,
  assetJson?: unknown,
) {
  return resolveAvatarFrameAsset(cosmeticCode, assetJson)?.frameUrl;
}

export function resolveAvatarFrameScale(
  frameUrl?: string,
  assetJson?: unknown,
  cosmeticCode?: string,
) {
  const asset = resolveAvatarFrameAsset(cosmeticCode, assetJson);
  if (asset?.scale) return asset.scale;
  if (frameUrl) {
    const matched = AVATAR_FRAME_CATALOG.find(
      (item) => item.asset.frameUrl === frameUrl,
    );
    if (matched?.asset.scale) return matched.asset.scale;
  }
  return 1.48;
}
