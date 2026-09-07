import { useEffect, useState } from 'react';

import {
  invalidatePageDataCache,
  invalidatePageDataCacheByPrefix,
} from '@/hooks/pageDataCache';

const COSMETIC_UPDATED_EVENT = 'gc:cosmetic-updated';
const DECORATION_CACHE_PREFIX = 'user-decorations:';
const SHOP_STORE_CACHE_PREFIX = 'shop:store:';
const SHOP_BACKPACK_CACHE_PREFIX = 'shop:backpack:';

/**
 * 装扮或背包发生变更后清理所有相关派生缓存，并广播给各展示位。
 *
 * 不能只刷新当前页面的筛选条件：商城/背包按分类、状态和分页分别缓存，
 * 任何一个快照残留都会造成“全部”和具体分类显示不一致。
 */
export function notifyCosmeticUpdated(accountId?: number | string) {
  invalidatePageDataCacheByPrefix(DECORATION_CACHE_PREFIX);

  if (accountId !== undefined) {
    const normalizedAccountId = String(accountId);
    invalidatePageDataCache(`${SHOP_STORE_CACHE_PREFIX}${normalizedAccountId}`);
    invalidatePageDataCacheByPrefix(
      `${SHOP_BACKPACK_CACHE_PREFIX}${normalizedAccountId}:`,
    );
  }

  window.dispatchEvent(new CustomEvent(COSMETIC_UPDATED_EVENT));
}

/** 订阅装扮变更，用于触发 decoration 重新请求 */
export function useCosmeticRefreshToken() {
  const [token, setToken] = useState(0);

  useEffect(() => {
    const handler = () => setToken((value) => value + 1);
    window.addEventListener(COSMETIC_UPDATED_EVENT, handler);
    return () => window.removeEventListener(COSMETIC_UPDATED_EVENT, handler);
  }, []);

  return token;
}
