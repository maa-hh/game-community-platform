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
  /** 评论/回复 optimistic 成功后由页面同步帖子互动总数，失败时传入 -1 回滚。 */
  onCommentCountChange?: (delta: number) => void;
  /** 底部横幅已带评论框时隐藏顶部输入 */
  hideComposer?: boolean;
  /** 评论首屏或刷新请求进行中；有旧评论时不替换已有内容。 */
  loading?: boolean;
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
  loading?: boolean;
}
