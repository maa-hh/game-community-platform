import type { IGameTag } from '@/types/game';
import { resolveGameCoverUrl } from '@/utils/steamImage';

export interface IGameTagRaw {
  appId: number;
  name: string;
  headerImage?: string;
}

export function mapGameTagsFromRaw(
  tags?: IGameTagRaw[] | null,
): IGameTag[] | undefined {
  if (!tags?.length) return undefined;

  const mapped: Array<IGameTag | null> = tags.map((tag) => {
    const appId = Number(tag.appId);
    if (!Number.isFinite(appId) || appId <= 0) return null;
    const name = tag.name.trim();
    if (!name) return null;
    return {
      appId,
      name,
      iconUrl: resolveGameCoverUrl(appId, tag.headerImage),
    };
  });
  return mapped.filter((tag): tag is IGameTag => tag !== null);
}

export function mergeGameTagOptions(
  current: IGameTag[],
  incoming: IGameTag[],
): IGameTag[] {
  const map = new Map(current.map((item) => [item.appId, item]));
  incoming.forEach((item) => map.set(item.appId, item));
  return Array.from(map.values());
}
