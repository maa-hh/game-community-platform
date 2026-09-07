import { useEffect, useRef, useState } from 'react';

import { fetchDecorationsBatchApi } from '@/service/cosmetic';
import type { IUserDecoration } from '@/types/cosmetic';
import { useCosmeticRefreshToken } from '@/utils/cosmeticRefresh';
import { getPageDataCache, setPageDataCache } from './pageDataCache';

function normalizeAccountIdsKey(accountIds: Array<number | undefined>) {
  return Array.from(
    new Set(accountIds.filter((id): id is number => Boolean(id && id > 0))),
  )
    .sort((a, b) => a - b)
    .join(',');
}

export function useUserDecorations(accountIds: Array<number | undefined>) {
  const [map, setMap] = useState<Record<number, IUserDecoration>>({});
  const [loading, setLoading] = useState(false);
  const refreshToken = useCosmeticRefreshToken();
  const previousRefreshTokenRef = useRef(refreshToken);
  const idsKey = normalizeAccountIdsKey(accountIds);

  useEffect(() => {
    const forceRefresh = previousRefreshTokenRef.current !== refreshToken;
    previousRefreshTokenRef.current = refreshToken;

    if (!idsKey) {
      setMap({});
      return;
    }

    const distinctIds = idsKey.split(',').map(Number);
    const cacheKey = `user-decorations:${idsKey}`;
    const cachedMap =
      getPageDataCache<Record<number, IUserDecoration>>(cacheKey);
    // 装备/卸下事件发生后，即使旧请求刚好把旧结果重新写入缓存，也必须
    // 跳过缓存重新请求，避免卸下后仍显示旧装扮。
    if (cachedMap && !forceRefresh) {
      setMap(cachedMap);
      setLoading(false);
      return undefined;
    }
    let cancelled = false;
    setLoading(true);

    void fetchDecorationsBatchApi(distinctIds)
      .then((res) => {
        if (cancelled || res.code !== 200 || !res.data) return;
        const normalized: Record<number, IUserDecoration> = {};
        Object.entries(res.data).forEach(([id, decoration]) => {
          normalized[Number(id)] = decoration;
        });
        setMap(normalized);
        setPageDataCache(cacheKey, normalized);
      })
      .catch(() => {
        // 刷新失败时保留旧装扮，避免页面先退回无装扮状态。
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [idsKey, refreshToken]);

  return {
    map,
    loading,
    get: (accountId?: number) => {
      if (!accountId) return undefined;
      return map[Number(accountId)];
    },
  };
}
