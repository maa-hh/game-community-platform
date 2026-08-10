import { useEffect, useState } from 'react';

import { fetchDecorationsBatchApi } from '@/service/cosmetic';
import type { IUserDecoration } from '@/types/cosmetic';
import { useCosmeticRefreshToken } from '@/utils/cosmeticRefresh';

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
  const idsKey = normalizeAccountIdsKey(accountIds);

  useEffect(() => {
    if (!idsKey) {
      setMap({});
      return;
    }

    const distinctIds = idsKey.split(',').map(Number);
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
      })
      .catch(() => {
        if (!cancelled) setMap({});
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
