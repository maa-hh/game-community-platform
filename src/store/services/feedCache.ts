import type { LatestPostItem } from '@/types/post';

export interface FeedPageLike {
  items: LatestPostItem[];
}

export type FeedItemsUpdate =
  LatestPostItem[] | ((items: LatestPostItem[]) => LatestPostItem[]);

/** 合并分页时按 ID 去重，并让后到的数据覆盖同 ID 的旧字段。 */
export function flattenFeedPages(pages: FeedPageLike[]): LatestPostItem[] {
  const itemsById = new Map<string, LatestPostItem>();

  for (const page of pages) {
    for (const item of page.items) {
      const previous = itemsById.get(item.id);
      itemsById.set(item.id, previous ? { ...previous, ...item } : item);
    }
  }

  return Array.from(itemsById.values());
}

/** 将互动类局部更新回写到 RTK Query 的分页缓存，保持原分页顺序。 */
export function applyFeedItemsUpdate(
  pages: FeedPageLike[],
  update: FeedItemsUpdate,
) {
  const current = flattenFeedPages(pages);
  const next = typeof update === 'function' ? update(current) : update;
  const nextById = new Map(next.map((item) => [item.id, item]));

  pages.forEach((page) => {
    page.items = page.items
      .map((item) => nextById.get(item.id) || item)
      .filter((item) => nextById.has(item.id));
  });
}
