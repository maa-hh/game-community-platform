import type { IGameListItem, IUserGameItem, IGamePrice } from '@/types/game';
import {
  resolveGameDisplayName,
  type IGameNameFields,
} from '@/utils/gameDisplayName';
import { resolveSteamReviewMetrics } from '@/utils/mapSteamReview';

type GameListItemRaw = IGameListItem &
  IGameNameFields & {
    steamReviewScore?: number;
    steamReviewCount?: number;
    price?: IGamePrice & {
      discountEndTime?: string | number;
    };
  };
type UserGameItemRaw = IUserGameItem & IGameNameFields;

export interface IGameListEnrichment {
  name?: string;
  genres?: string[];
  developer?: string;
  publisher?: string;
  releaseDate?: string;
  steamScore?: number;
  avgScore?: number;
  reviewCount?: number;
}

function mapGamePrice(raw?: GameListItemRaw['price']): IGamePrice | undefined {
  if (!raw) return undefined;
  const mapped = {
    ...raw,
    discountEndAt: raw.discountEndAt ?? raw.discountEndTime,
  };
  // Steam 轻量榜单偶尔会带一个“非免费但金额为 0”的占位 price，
  // 不能让它覆盖详情同步出的真实价格并显示为 ¥0.00。
  if (
    !mapped.free &&
    (mapped.finalPrice ?? 0) === 0 &&
    (mapped.initial ?? 0) === 0 &&
    !mapped.formatted?.trim()
  ) {
    return undefined;
  }
  return mapped;
}

function mapSharedGameFields(raw: GameListItemRaw | UserGameItemRaw) {
  const steamReview = resolveSteamReviewMetrics(raw);

  return {
    appId: raw.appId,
    name: resolveGameDisplayName(raw) || `游戏 ${raw.appId}`,
    coverUrl: raw.coverUrl,
    genres: raw.genres,
    developer: raw.developer,
    publisher: raw.publisher,
    releaseDate: raw.releaseDate,
    steamScore: steamReview.score,
    steamReviewCount: steamReview.count,
    avgScore: raw.avgScore != null ? Number(raw.avgScore) : undefined,
    reviewCount: raw.reviewCount ?? 0,
    discussCount: raw.discussCount ?? 0,
    rank: 'rank' in raw && raw.rank != null ? Number(raw.rank) : undefined,
    price: 'price' in raw ? mapGamePrice(raw.price) : undefined,
  };
}

export function mapGameListItem(raw: GameListItemRaw): IGameListItem {
  return mapSharedGameFields(raw);
}

export function mapUserGameItem(raw: UserGameItemRaw): IUserGameItem {
  return {
    ...mapSharedGameFields(raw),
    source: raw.source,
    steamOwned: raw.steamOwned,
    playtimeForever: raw.playtimeForever,
    followTime: raw.followTime,
  };
}

export function applyGameListEnrichment<T extends IGameListItem>(
  items: T[],
  enrichment: Record<number, IGameListEnrichment>,
): T[] {
  return items.map((item) => {
    const extra = enrichment[item.appId];
    if (!extra) return item;

    return {
      ...item,
      ...(extra.name ? { name: extra.name } : null),
      genres: extra.genres ?? item.genres,
      developer: extra.developer ?? item.developer,
      publisher: extra.publisher ?? item.publisher,
      releaseDate: extra.releaseDate ?? item.releaseDate,
      steamScore: extra.steamScore ?? item.steamScore,
      avgScore: extra.avgScore ?? item.avgScore,
      reviewCount: extra.reviewCount ?? item.reviewCount,
    };
  });
}

/** @deprecated 使用 applyGameListEnrichment */
export function applyCanonicalGameNames<
  T extends { appId: number; name: string },
>(items: T[], canonicalNames: Record<number, string>): T[] {
  const enrichment = Object.fromEntries(
    Object.entries(canonicalNames).map(([appId, name]) => [
      Number(appId),
      { name },
    ]),
  ) as Record<number, IGameListEnrichment>;

  return applyGameListEnrichment(items as IGameListItem[], enrichment) as T[];
}
