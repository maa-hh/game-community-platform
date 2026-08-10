import type { IShopItem } from '@/service/shop';

export type ShopOwnedFilter = 'all' | 'unowned' | 'owned';
export type ShopPriceSort = 'default' | 'asc' | 'desc';

export interface ShopItemFilterState {
  owned: ShopOwnedFilter;
  priceSort: ShopPriceSort;
}

export const SHOP_OWNED_FILTER_OPTIONS = [
  { label: '全部', value: 'all' as const },
  { label: '未拥有', value: 'unowned' as const },
  { label: '已拥有', value: 'owned' as const },
];

export const SHOP_PRICE_SORT_OPTIONS = [
  { label: '默认排序', value: 'default' as const },
  { label: '价格从低到高', value: 'asc' as const },
  { label: '价格从高到低', value: 'desc' as const },
];

export function applyShopItemFilters(
  items: IShopItem[],
  filters: ShopItemFilterState,
): IShopItem[] {
  let result = items;

  if (filters.owned === 'owned') {
    result = result.filter((item) => item.owned);
  } else if (filters.owned === 'unowned') {
    result = result.filter((item) => !item.owned);
  }

  return [...result].sort((a, b) => {
    if (filters.owned === 'all' && Boolean(a.owned) !== Boolean(b.owned)) {
      return a.owned ? 1 : -1;
    }

    if (filters.priceSort === 'asc') {
      return a.pricePoints - b.pricePoints;
    }
    if (filters.priceSort === 'desc') {
      return b.pricePoints - a.pricePoints;
    }

    return a.id - b.id;
  });
}
