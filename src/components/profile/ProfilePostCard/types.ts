import type { ReactNode } from 'react';

import type { PostRowActionItem } from '@/base-ui/PostRowActionBar';
import type { PostRowPreviewData } from '@/base-ui/PostRowPreview';
import type { FeedItemData } from '@/types/profile';

export interface ProfilePostCardProps {
  item: FeedItemData;
  actionItems?: PostRowActionItem[];
  footer?: ReactNode;
  onPreviewClick: () => void;
}

export function mapFeedItemToRowPreview(
  item: FeedItemData,
): PostRowPreviewData {
  return {
    id: item.id,
    title: item.title,
    summary: item.summary,
    content: item.content,
    postType: item.postType,
    coverUrl: item.coverUrl,
    videoUrl: item.videoUrl,
    images: item.images?.length
      ? item.images
      : item.cover
        ? [item.cover]
        : undefined,
    refPost: item.refPost,
    gameTags: item.gameTags,
    createdAt: item.createdAt,
  };
}
