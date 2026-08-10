import type { Dispatch, RefObject, SetStateAction } from 'react';

import type { PostComment } from '@/types/post';

export interface CommentInfiniteProps {
  sentinelRef?: RefObject<HTMLDivElement | null>;
  loadingMore?: boolean;
  hasMore?: boolean;
  totalCount?: number;
}

export interface ICommentSectionProps {
  articleId: string;
  comments: PostComment[];
  onChange: Dispatch<SetStateAction<PostComment[]>>;
  /** 底部横幅已带评论框时隐藏顶部输入 */
  hideComposer?: boolean;
  infinite?: CommentInfiniteProps;
  highlight?: {
    commentId?: string;
    replyId?: string;
  };
}

export type ReplyTarget = {
  commentId: string;
  accountId: number;
  nickname: string;
  /** 回复某条回复时，插在其后方 */
  afterReplyId?: string;
};

export interface CommentComposerProps {
  draft: string;
  submitting: boolean;
  onDraftChange: (value: string) => void;
  onSubmit: () => void;
  onFocusRequireLogin: () => void;
}

export interface CommentListProps {
  articleId: string;
  comments: PostComment[];
  myAccountId?: number;
  expandedReplyIds: Record<string, boolean>;
  onToggleExpand: (commentId: string) => void;
  onLoadMoreReplies?: (commentId: string) => void;
  replyLoadingIds?: Record<string, boolean>;
  onChange: Dispatch<SetStateAction<PostComment[]>>;
  requireLogin: () => boolean;
  onOpenReply: (target: ReplyTarget) => void;
  onReport: (targetType: 'comment' | 'reply', targetId: string) => void;
  patchComment: (commentId: string, patch: Partial<PostComment>) => void;
  onCommentLike: (commentId: string) => void;
  onReplyLike: (commentId: string, replyId: string) => void;
  infinite?: CommentInfiniteProps;
}
