import hyRequest from './request';
import type { IDataType } from './types';
import type { IUserGameItem } from '@/types/game';
import { applyGameListEnrichment, mapUserGameItem } from '@/utils/mapGameItem';
import { fetchGameListEnrichmentApi } from '@/service/game';

export async function fetchMyFollowedGamesApi() {
  const res = await hyRequest.get<IDataType<IUserGameItem[]>>({
    url: '/steam/follows',
  });
  const mapped = (res.data || []).map((item) => mapUserGameItem(item));
  const enrichment = await fetchGameListEnrichmentApi(
    mapped.map((item) => item.appId),
  );

  return {
    ...res,
    data: applyGameListEnrichment(mapped, enrichment),
  };
}

export function checkGameFollowApi(appId: number) {
  return hyRequest.get<IDataType<boolean>>({
    url: `/steam/follows/check/${appId}`,
  });
}

export function checkGameFollowBatchApi(appIds: number[]) {
  if (appIds.length === 0) {
    return Promise.resolve({ data: {} as Record<number, boolean> });
  }
  return hyRequest.post<IDataType<Record<number, boolean>>>({
    url: '/steam/follows/check-batch',
    data: { appIds },
  });
}

export function followGameApi(
  appId: number,
  source: 'manual' | 'discover' | 'steam_import' = 'manual',
) {
  return hyRequest.post<IDataType<null>>({
    url: '/steam/follows',
    data: { appId, source },
  });
}

export function unfollowGameApi(appId: number) {
  return hyRequest.delete<IDataType<null>>({
    url: `/steam/follows/${appId}`,
  });
}

export function importSteamGamesToFollowsApi() {
  return hyRequest.post<IDataType<{ imported: number }>>({
    url: '/steam/follows/import-steam',
  });
}
