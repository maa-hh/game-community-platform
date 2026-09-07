import type { ReactNode } from 'react';

import type { PostComment, PostReply } from '@/types/post';

import type { IUserDecoration } from '@/types/cosmetic';

export interface ICommentItemProps {
  comment: PostComment;
  myAccountId?: number;
  expanded: boolean;
  onToggleExpand: () => void;
  onLoadMoreReplies?: () => void;
  replyLoading?: boolean;
  onLike: () => void;
  onReply: () => void;
  onReport?: () => void;
  onDelete?: () => void;
  onReplyLike: (replyId: string) => void;
  onReplyToReply: (reply: PostReply) => void;
  onReplyReport?: (reply: PostReply) => void;
  onReplyDelete?: (reply: PostReply) => void;
  decoration?: IUserDecoration;
  getReplyDecoration?: (accountId?: number) => IUserDecoration | undefined;
  metaExtra?: ReactNode;
}
