import type { LatestPostItem } from '@/types/post';

/** 热榜列表按 rank 升序（1 → N），无 rank 的排到末尾 */
export function sortHotRankItems(items: LatestPostItem[]): LatestPostItem[] {
  return [...items].sort((a, b) => {
    const rankA =
      a.rank != null && a.rank > 0 ? a.rank : Number.MAX_SAFE_INTEGER;
    const rankB =
      b.rank != null && b.rank > 0 ? b.rank : Number.MAX_SAFE_INTEGER;
    if (rankA !== rankB) return rankA - rankB;
    return (b.hotScore ?? 0) - (a.hotScore ?? 0);
  });
}
