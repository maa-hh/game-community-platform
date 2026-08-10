import hyRequest from '@/service/request';
import type { IPageResult } from '@/service/types';

export type RepurchasePolicy =
  'ONCE_FOREVER' | 'UNLIMITED' | 'COOLDOWN' | 'LIMIT_PER_WINDOW';

export interface IShopItem {
  id: number;
  name: string;
  description?: string;
  cosmeticCode: string;
  pricePoints: number;
  grantQuantity?: number;
  stock?: number;
  icon?: string;
  status?: number;
  repurchasePolicy?: RepurchasePolicy;
  limitCount?: number;
  limitWindowSeconds?: number;
  beginTime?: string;
  endTime?: string;
  owned?: boolean;
  equipped?: boolean;
  canBuy?: boolean;
  cannotBuyReason?: string;
  nextBuyAt?: string;
}

export interface IShopCurrency {
  points: number;
}

export interface IExchangeResult {
  orderNo: string;
  cosmeticCode: string;
  grantQuantity?: number;
  pointsBalance?: number;
  status?: number;
  statusText?: string;
}

export interface IShopOrder {
  orderNo: string;
  requestId?: string;
  itemId: number;
  itemName?: string;
  itemIcon?: string;
  cosmeticCode: string;
  quantity: number;
  pricePoints: number;
  totalPoints: number;
  grantQuantity?: number;
  status: number;
  statusText?: string;
  failReason?: string;
  createTime?: string;
  payTime?: string;
  completeTime?: string;
  expireTime?: string;
}

export function fetchShopItemsApi(params: {
  page?: number;
  size?: number;
  status?: number;
}) {
  return hyRequest.get<IPageResult<IShopItem>>({
    url: '/shop/item/page',
    params: { ...params, status: params.status ?? 1 },
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

export function fetchShopOrderApi(orderNo: string) {
  return hyRequest.get<{
    code: number;
    data: IShopOrder;
    message: string;
  }>({
    url: `/shop/order/${encodeURIComponent(orderNo)}`,
  });
}

export function payShopOrderApi(orderNo: string) {
  return hyRequest.post<{ code: number; data: null; message: string }>({
    url: '/shop/order/pay',
    data: { orderNo },
  });
}
