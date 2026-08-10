import type { MouseEventHandler } from 'react';

import type { PostRowPreviewData } from '@/base-ui/PostRowPreview';
import type { LatestPostItem } from '@/types/post';

export interface HotRankRowCardProps {
  item: LatestPostItem;
  rank?: number;
  hotScore?: number;
  className?: string;
  onClick?: MouseEventHandler<HTMLElement>;
  onLikeClick?: MouseEventHandler<HTMLButtonElement>;
}

export function mapLatestPostToRowPreview(
  item: LatestPostItem,
): PostRowPreviewData {
  return {
    id: item.id,
    title: item.title,
    summary: item.summary,
    content: item.content,
    postType: item.postType,
    coverUrl: item.coverUrl,
    videoUrl: item.videoUrl,
    images: item.images,
    refPost: item.refPost,
    gameTags: item.gameTags,
  };
}
