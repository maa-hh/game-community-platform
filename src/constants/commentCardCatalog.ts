import type { ICommentCardAsset } from '@/types/cosmetic';
import { parseCommentCardAsset } from '@/utils/cosmeticAsset';

export interface ICommentCardCatalogItem {
  code: string;
  name: string;
  asset: ICommentCardAsset;
}

export const COMMENT_CARD_CATALOG: ICommentCardCatalogItem[] = [
  {
    code: 'comment_card_neon',
    name: '霓虹评论卡片',
    asset: {
      bg: '#1a1a2e',
      border: '1px solid #00d4ff',
      radius: 12,
      textTheme: 'light',
    },
  },
];

const COMMENT_CARD_ASSET_MAP = new Map(
  COMMENT_CARD_CATALOG.map((item) => [item.code, item.asset]),
);

export function isCommentCardCosmeticCode(code?: string) {
  return Boolean(code?.startsWith('comment_card_'));
}

export function resolveCommentCardAsset(
  cosmeticCode?: string,
  assetJson?: unknown,
): ICommentCardAsset | undefined {
  const parsed = parseCommentCardAsset(assetJson);
  if (parsed?.bg || parsed?.border) return parsed;
  if (!cosmeticCode) return undefined;
  return COMMENT_CARD_ASSET_MAP.get(cosmeticCode);
}
