import hyRequest from '@/service/request';
import type { IPageResult } from '@/service/types';

import type { IUserCosmeticItem, IUserDecoration } from '@/types/cosmetic';

export interface CosmeticBackpackQuery {
  page?: number;
  size?: number;
  effectMode?: 'EQUIP' | 'CONSUMABLE';
  category?: string;
  equipped?: boolean;
  state?: 'ACTIVE' | 'EXPIRED';
  keyword?: string;
}

export function fetchUserDecorationApi(accountId: number) {
  return hyRequest.get<{
    code: number;
    data: IUserDecoration;
    message: string;
  }>({
    url: `/user/cosmetic/decoration/${accountId}`,
  });
}

/** 批量查询公开装扮；入参和返回 key 均为对外 accountId。 */
export function fetchDecorationsBatchApi(accountIds: number[]) {
  return hyRequest.post<{
    code: number;
    data: Record<string, IUserDecoration>;
    message: string;
  }>({
    url: '/user/cosmetic/decorations/batch',
    data: { accountIds },
  });
}

export function fetchCosmeticBackpackApi(params: CosmeticBackpackQuery = {}) {
  return hyRequest.get<IPageResult<IUserCosmeticItem>>({
    url: '/user/cosmetic/backpack/page',
    params,
  });
}

export function equipCosmeticApi(payload: { slot: string; code: string }) {
  return hyRequest.put<{ code: number; data: null; message: string }>({
    url: '/user/cosmetic/equip',
    data: payload,
  });
}

export function unequipCosmeticApi(payload: { slot: string }) {
  return hyRequest.put<{ code: number; data: null; message: string }>({
    url: '/user/cosmetic/unequip',
    data: payload,
  });
}

export function consumeCosmeticApi(payload: { code: string }) {
  return hyRequest.post<{ code: number; data: null; message: string }>({
    url: '/user/cosmetic/use',
    data: payload,
  });
}
