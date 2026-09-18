import type { ContentCardPostType } from '@/types/content';

/** 后端 postType 数值 → 展示类型，与 service/content.ts 的 POST_TYPE 对齐。 */
export function mapNumericPostType(value?: number | null): ContentCardPostType {
  if (value === 4) return 'repost';
  if (value === 3) return 'video';
  if (value === 2) return 'article';
  return 'image_text';
}

const POST_TYPE_NUMERIC: Record<ContentCardPostType, number> = {
  image_text: 1,
  article: 2,
  video: 3,
  repost: 4,
};

export function mapContentPostTypeToNumeric(
  type?: ContentCardPostType,
): number | undefined {
  if (!type) return undefined;
  return POST_TYPE_NUMERIC[type];
}

export function resolvePostType(data: {
  postType?: ContentCardPostType;
  mediaType?: 'image' | 'video';
}): ContentCardPostType {
  if (data.postType) return data.postType;
  if (data.mediaType === 'video') return 'video';
  return 'image_text';
}
