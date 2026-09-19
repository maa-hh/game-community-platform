import hyRequest from '@/service/request';
import type { IPageResult } from '@/service/types';

export type RepurchasePolicy =
  'ONCE_FOREVER' | 'UNLIMITED' | 'COOLDOWN' | 'LIMIT_PER_WINDOW';

export const SHOP_ORDER_STATUS = {
  FAILED: -1,
  CANCELLED: 0,
  CREATING: 1,
  PENDING_PAY: 2,
  PAID: 3,
  COMPLETED: 4,
} as const;

export type ShopOrderStatus =
  (typeof SHOP_ORDER_STATUS)[keyof typeof SHOP_ORDER_STATUS];

export interface IShopItem {
  id: number;
  name: string;
  description: string;
  cosmeticCode: string;
  pricePoints: number;
  grantQuantity: number;
  stock: number;
  icon: string;
  status: ShopOrderStatus;
  repurchasePolicy: RepurchasePolicy;
  limitCount: number;
  limitWindowSeconds: number;
  beginTime: string;
  endTime: string;
  owned: boolean;
  equipped: boolean;
  canBuy: boolean;
  cannotBuyReason: string;
  nextBuyAt: string;
}

export interface IShopCurrency {
  points: number;
}

export interface IExchangeResult {
  orderNo: string;
  cosmeticCode: string;
  grantQuantity: number;
  pointsBalance: number;
  status: number;
  statusText: string;
}

export function fetchShopItemsApi(params: {
  page?: number;
  size?: number;
  status?: number;
  /** 游客浏览时跳过旧会话 refresh；登录用户保留个性化拥有状态。 */
  skipAuth?: boolean;
}) {
  const { skipAuth, ...query } = params;
  return hyRequest.get<IPageResult<IShopItem>>({
    url: '/shop/item/page',
    params: { ...query, status: query.status ?? 1 },
    skipAuth,
  });
}

export function fetchShopCurrencyApi() {
  return hyRequest.get<{ code: number; data: IShopCurrency; message: string }>({
    url: '/shop/currency/me',
  });
}

export function exchangeShopItemApi(payload: {
  itemId: number;
  quantity?: number;
  requestId: string;
}) {
  return hyRequest.post<{
    code: number;
    data: IExchangeResult;
    message: string;
  }>({
    url: '/shop/exchange',
    data: payload,
  });
}
