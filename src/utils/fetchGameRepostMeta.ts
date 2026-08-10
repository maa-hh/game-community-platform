import { fetchGameDetailApi } from '@/service/game';

/** 批量拉取游戏转发引用卡元信息 */
export async function fetchGameRepostMetaMap(
  appIds: number[],
): Promise<
  Record<number, { name?: string; summary?: string; coverUrl?: string }>
> {
  const unique = Array.from(new Set(appIds.filter((id) => id > 0)));
  if (unique.length === 0) return {};

  const entries = await Promise.all(
    unique.map(async (appId) => {
      try {
        const res = await fetchGameDetailApi(appId);
        const detail = res.data;
        return [
          appId,
          {
            name: detail.name,
            summary: detail.shortDescription || detail.description,
            coverUrl: detail.coverUrl,
          },
        ] as const;
      } catch {
        return null;
      }
    }),
  );

  const result: Record<
    number,
    { name?: string; summary?: string; coverUrl?: string }
  > = {};
  for (const entry of entries) {
    if (entry) result[entry[0]] = entry[1];
  }
  return result;
}
