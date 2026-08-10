import { isAvatarFrameCosmeticCode } from '@/constants/avatarFrameCatalog';
import { isCommentCardCosmeticCode } from '@/constants/commentCardCatalog';
import { isProfileBgCosmeticCode } from '@/constants/profileBgCatalog';
import type { CosmeticSlot } from '@/types/cosmetic';

export type ShopCategory =
  'all' | 'avatar_frame' | 'profile_bg' | 'comment_card';

/** 已从商城下架、不在分类 Tab 中展示的类型 */
const HIDDEN_SHOP_CATEGORIES = new Set(['comment_font', 'post_card']);

export const SHOP_CATEGORY_OPTIONS: {
  label: string;
  value: ShopCategory;
}[] = [
  { label: '全部', value: 'all' },
  { label: '头像挂件', value: 'avatar_frame' },
  { label: '主页背景', value: 'profile_bg' },
  { label: '评论卡片', value: 'comment_card' },
];

function resolveItemCategory(
  cosmeticCode?: string,
  slot?: CosmeticSlot,
): string | undefined {
  if (slot === 'PROFILE_BG' || isProfileBgCosmeticCode(cosmeticCode)) {
    return 'profile_bg';
  }
  if (slot === 'AVATAR_FRAME' || isAvatarFrameCosmeticCode(cosmeticCode)) {
    return 'avatar_frame';
  }
  if (slot === 'COMMENT_CARD' || isCommentCardCosmeticCode(cosmeticCode)) {
    return 'comment_card';
  }
  if (slot === 'COMMENT_FONT' || cosmeticCode?.startsWith('comment_font_')) {
    return 'comment_font';
  }
  if (slot === 'POST_CARD' || cosmeticCode?.startsWith('post_card_')) {
    return 'post_card';
  }
  return undefined;
}

export function isHiddenShopItem(item: {
  cosmeticCode: string;
  slot?: CosmeticSlot;
}): boolean {
  const category = resolveItemCategory(item.cosmeticCode, item.slot);
  return category != null && HIDDEN_SHOP_CATEGORIES.has(category);
}

export function resolveShopCategory(
  cosmeticCode?: string,
  slot?: CosmeticSlot,
): ShopCategory | undefined {
  const category = resolveItemCategory(cosmeticCode, slot);
  if (!category || HIDDEN_SHOP_CATEGORIES.has(category)) return undefined;
  return category as ShopCategory;
}

export function filterByShopCategory<
  T extends { cosmeticCode: string; slot?: CosmeticSlot },
>(items: T[], category: ShopCategory): T[] {
  const visible = items.filter((item) => !isHiddenShopItem(item));
  if (category === 'all') return visible;
  return visible.filter(
    (item) => resolveShopCategory(item.cosmeticCode, item.slot) === category,
  );
}
