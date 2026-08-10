import type { ContentCardPostType } from '@/types/content';

export type FeedTypeFilter = 'all' | Exclude<ContentCardPostType, 'article'>;

export const feedTypeTabs: { label: string; value: FeedTypeFilter }[] = [
  { label: '全部', value: 'all' },
  { label: '图文', value: 'image_text' },
  { label: '视频', value: 'video' },
  { label: '转发', value: 'repost' },
];

export const feedEmptyText: Record<FeedTypeFilter, string> = {
  all: '还没有动态，去关注感兴趣的人或发布第一条吧',
  image_text: '暂无图文动态',
  video: '暂无视频动态',
  repost: '暂无转发动态',
};
