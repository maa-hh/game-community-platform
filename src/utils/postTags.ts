import type { ContentCardPostType } from '@/types/content';

export function getPostTypeLabel(postType: ContentCardPostType): string {
  switch (postType) {
    case 'video':
      return '视频';
    case 'article':
      return '图文';
    case 'repost':
      return '转发';
    default:
      return '图文';
  }
}
