import type { LatestPostItem } from '@/types/post';

function resolveFeedSortKey(item: LatestPostItem): number {
  if (item.sortTime) {
    const ts = Date.parse(item.sortTime);
    if (Number.isFinite(ts)) return ts;
  }
  const id = Number(item.id);
  return Number.isFinite(id) ? id : 0;
}

/** 信息流按时间降序（新 → 旧），与接口默认顺序一致 */
export function sortFeedItemsByTime(items: LatestPostItem[]): LatestPostItem[] {
  return [...items].sort(
    (a, b) => resolveFeedSortKey(b) - resolveFeedSortKey(a),
  );
}
