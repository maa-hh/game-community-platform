import type { ICategory } from '@/service/content';
import type { ContentCardTag } from '@/types/content';
import type { IArticleRaw } from '@/utils/mapPost';

const POST_TYPE_LABELS = new Set(['图文', '文章', '视频', '转发']);

export function categoryTagIconUrl(
  categoryId: number,
  iconUrl?: string | null,
): string {
  if (iconUrl?.trim()) return iconUrl.trim();
  return `https://picsum.photos/seed/gc-category-${categoryId}/32/32`;
}

export function resolveCategoryIds(raw: {
  categoryId?: number;
  categoryIds?: number[];
}): number[] {
  if (raw.categoryIds?.length) return raw.categoryIds.slice(0, 3);
  if (raw.categoryId != null) return [raw.categoryId];
  return [];
}

/** 文章 → 分区标签（带图标） */
export function buildCategoryTags(
  raw: Pick<IArticleRaw, 'categoryId' | 'categoryIds' | 'categoryNames'>,
  categoryMap: Map<number, ICategory>,
): ContentCardTag[] {
  const ids = resolveCategoryIds(raw);
  if (ids.length > 0) {
    const tags: ContentCardTag[] = [];
    ids.forEach((id) => {
      const category = categoryMap.get(id);
      if (!category) return;
      tags.push({
        text: category.name,
        icon: categoryTagIconUrl(category.id, category.iconUrl),
      });
    });
    return tags;
  }

  return (raw.categoryNames || []).slice(0, 3).map((text, index) => ({
    text,
    icon: categoryTagIconUrl(index + 1),
  }));
}

/** 列表展示用：过滤掉帖子类型标签，仅保留分区等业务标签 */
export function resolvePostDisplayTags(
  tags: ContentCardTag[] | undefined,
): ContentCardTag[] {
  return (tags ?? []).filter((tag) => !POST_TYPE_LABELS.has(tag.text));
}
