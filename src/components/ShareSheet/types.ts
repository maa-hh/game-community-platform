import type { ReactNode } from 'react';
import type { ContentCardPostType } from '@/types/content';
import type { PostAuthor } from '@/types/post';

export interface IShareSheetProps {
  open: boolean;
  articleId: string;
  articleTitle: string;
  articleSummary?: string;
  coverUrl?: string;
  videoUrl?: string;
  postType?: ContentCardPostType;
  author: PostAuthor;
  viewCount?: number;
  commentCount?: number;
  likeCount?: number;
  onClose: () => void;
  onShared?: (shareCount: number) => void;
  onReposted?: (newPostId: string) => void;
}

export interface ShareActionItem {
  key: 'copy' | 'repost';
  icon: ReactNode;
  label: string;
  desc: string;
  onClick: () => void;
}

export interface ShareRepostModalProps {
  open: boolean;
  variant: 'post' | 'game';
  targetName: string;
  title: string;
  content: string;
  submitting: boolean;
  onTitleChange: (value: string) => void;
  onContentChange: (value: string) => void;
  onCancel: () => void;
  onSubmit: () => void;
}
