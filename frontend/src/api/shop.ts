import { request, requestEnvelope } from "./client";
import { PageEnvelope } from "./social";

export type ShopItem = {
  id: number;
  name: string;
  description?: string;
  price: number;
  productType: number;
  businessId?: number;
  stock: number;
  icon?: string;
  status: number;
  businessCode?: string;
  quantity: number;
  limitCount: number;
  beginTime?: string;
  endTime?: string;
};

export type ShopOrder = {
  orderNo: string;
  requestId: string;
  itemId: number;
  itemName: string;
  itemIcon?: string;
  productType: number;
  quantity: number;
  payType: number;
  originalPrice: number;
  discountAmount: number;
  finalPrice: number;
  couponId?: number;
  userCouponId?: number;
  businessCode?: string;
  status: number;
  statusText: string;
  failReason?: string;
  createTime?: string;
  payTime?: string;
  expireTime?: string;
};

export type ShopCurrency = {
  gold: number;
  diamond: number;
};

export type ShopCoupon = {
  couponId: number;
  couponName: string;
  discountType: number;
  discountValue: number;
  minAmount: number;
  scopeType: number;
  scopeItemId?: number;
  scopeProductType?: number;
  expireTime?: string;
  userCouponId: number;
  status: number;
  statusText: string;
};

export const shopApi = {
  listItems: (payload?: { page?: number; size?: number; productType?: number; status?: number }) => {
    const params = new URLSearchParams({
      page: String(payload?.page ?? 1),
      size: String(payload?.size ?? 12)
    });
    if (payload?.productType !== undefined) {
      params.set("productType", String(payload.productType));
    }
    if (payload?.status !== undefined) {
      params.set("status", String(payload.status));
    }
    return requestEnvelope<ShopItem[]>(`/shop/item/page?${params.toString()}`) as Promise<PageEnvelope<ShopItem>>;
  },
  getCurrency: () => request<ShopCurrency>("/shop/currency/me"),
  listUserCoupons: (payload?: { page?: number; size?: number; status?: number; itemId?: number }) => {
    const params = new URLSearchParams({
      page: String(payload?.page ?? 1),
      size: String(payload?.size ?? 20)
    });
    if (payload?.status !== undefined) {
      params.set("status", String(payload.status));
    }
    if (payload?.itemId !== undefined) {
      params.set("itemId", String(payload.itemId));
    }
    return requestEnvelope<ShopCoupon[]>(`/shop/user-coupon/list?${params.toString()}`) as Promise<PageEnvelope<ShopCoupon>>;
  },
  createOrder: (payload: { itemId: number; quantity: number; payType: number; requestId: string; userCouponId?: number }) =>
    request<ShopOrder>("/shop/order", { method: "POST", body: payload }),
  getOrder: (orderNo: string) => request<ShopOrder>(`/shop/order/${orderNo}`),
  listOrders: (payload?: { page?: number; size?: number }) => {
    const params = new URLSearchParams({
      page: String(payload?.page ?? 1),
      size: String(payload?.size ?? 10)
    });
    return requestEnvelope<ShopOrder[]>(`/shop/order/page?${params.toString()}`) as Promise<PageEnvelope<ShopOrder>>;
  },
  payOrder: (orderNo: string) => request<void>("/shop/order/pay", { method: "POST", body: { orderNo } }),
  cancelOrder: (orderNo: string) => request<void>(`/shop/order/${orderNo}/cancel`, { method: "POST" })
};
