import type { IProfileBgAsset } from '@/types/cosmetic';
import { parseProfileBgAsset } from '@/utils/cosmeticAsset';

export interface IProfileBgCatalogItem {
  code: string;
  name: string;
  asset: IProfileBgAsset;
}

/** 主页背景本地资源目录（与 public/cosmetic/profile-bg 及 SQL 种子一致） */
export const PROFILE_BG_CATALOG: IProfileBgCatalogItem[] = [
  {
    code: 'profile_bg_hornet_soft',
    name: '丝之拥',
    asset: {
      bgImage: '/cosmetic/profile-bg/hornet-soft.png',
      objectPosition: 'center center',
      overlayGradient:
        'to bottom, rgba(255,255,255,0.25) 0%, rgba(255,255,255,0.72) 100%',
      textTheme: 'dark',
    },
  },
  {
    code: 'profile_bg_hornet_fluffy',
    name: '绒毛小憩',
    asset: {
      bgImage: '/cosmetic/profile-bg/hornet-fluffy.png',
      objectPosition: 'center 32%',
      overlayGradient:
        'to bottom, rgba(255,255,255,0.2) 0%, rgba(255,240,245,0.78) 100%',
      textTheme: 'dark',
    },
  },
  {
    code: 'profile_bg_hollow_mask',
    name: '破碎面具',
    asset: {
      bgImage: '/cosmetic/profile-bg/hollow-mask.png',
      objectPosition: 'center center',
      overlayGradient: 'to bottom, rgba(0,0,0,0.15) 0%, rgba(0,0,0,0.78) 100%',
      textTheme: 'light',
    },
  },
  {
    code: 'profile_bg_radiance_void',
    name: '辉光降临',
    asset: {
      bgImage: '/cosmetic/profile-bg/radiance-void.png',
      objectPosition: 'center 45%',
      overlayGradient: 'to bottom, rgba(0,0,0,0.2) 0%, rgba(0,0,0,0.85) 100%',
      textTheme: 'light',
    },
  },
  {
    code: 'profile_bg_silver_uniform',
    name: '银发校服',
    asset: {
      bgImage: '/cosmetic/profile-bg/silver-uniform.png',
      objectPosition: 'center 68%',
      overlayGradient:
        'to bottom, rgba(255,255,255,0.35) 0%, rgba(245,248,252,0.88) 100%',
      textTheme: 'dark',
    },
  },
  {
    code: 'profile_bg_summer_sweet',
    name: '夏日甜筒',
    asset: {
      bgImage: '/cosmetic/profile-bg/summer-sweet.png',
      objectPosition: 'center 30%',
      overlayGradient:
        'to bottom, rgba(255,255,255,0.18) 0%, rgba(255,248,240,0.75) 100%',
      textTheme: 'dark',
    },
  },
  {
    code: 'profile_bg_angel_city',
    name: '都市天使',
    asset: {
      bgImage: '/cosmetic/profile-bg/angel-city.png',
      objectPosition: 'center center',
      overlayGradient: 'to bottom, rgba(0,0,0,0.2) 0%, rgba(20,0,0,0.82) 100%',
      textTheme: 'light',
    },
  },
  {
    code: 'profile_bg_angel_melancholy',
    name: '融化的忧伤',
    asset: {
      bgImage: '/cosmetic/profile-bg/angel-melancholy.png',
      objectPosition: 'center 35%',
      overlayGradient:
        'to bottom, rgba(255,255,255,0.3) 0%, rgba(250,250,250,0.9) 100%',
      textTheme: 'dark',
    },
  },
  {
    code: 'profile_bg_sakura_dream',
    name: '樱粉花梦',
    asset: {
      bgImage: '/cosmetic/profile-bg/sakura-dream.png',
      objectPosition: 'center 30%',
      overlayGradient:
        'to bottom, rgba(0,0,0,0.18) 0%, rgba(30,10,30,0.8) 100%',
      textTheme: 'light',
    },
  },
];

const PROFILE_BG_ASSET_MAP = new Map(
  PROFILE_BG_CATALOG.map((item) => [item.code, item.asset]),
);

export function isProfileBgCosmeticCode(code?: string) {
  return Boolean(code?.startsWith('profile_bg_'));
}

export function resolveProfileBgAsset(
  cosmeticCode?: string,
  assetJson?: unknown,
  fallbackIcon?: string,
): IProfileBgAsset | undefined {
  const parsed = parseProfileBgAsset(assetJson);
  if (parsed?.bgImage) return parsed;
  if (cosmeticCode) {
    const catalogAsset = PROFILE_BG_ASSET_MAP.get(cosmeticCode);
    if (catalogAsset?.bgImage) return catalogAsset;
  }
  if (
    cosmeticCode &&
    isProfileBgCosmeticCode(cosmeticCode) &&
    fallbackIcon?.trim()
  ) {
    return {
      bgImage: fallbackIcon,
      overlay: 'rgba(0,0,0,0.2)',
      textTheme: 'dark',
    };
  }
  return undefined;
}
