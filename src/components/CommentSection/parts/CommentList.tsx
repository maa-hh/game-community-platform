import React, { memo, useMemo } from 'react';
import type { FC } from 'react';
import { message } from 'antd';

import ListEndHint from '@/base-ui/ListEndHint';
import CommentItem from '@/components/CommentItem';
import { useUserDecorations } from '@/hooks/useUserDecorations';
import { deleteCommentApi, deleteReplyApi } from '@/service/social';
import { formatApiError } from '@/utils/apiError';

import type { CommentListProps } from '../types';

const CommentList: FC<CommentListProps> = ({
  articleId,
  comments,
  myAccountId,
  expandedReplyIds,
  onToggleExpand,
  onLoadMoreReplies,
  replyLoadingIds,
  onChange,
  requireLogin,
  onOpenReply,
  onReport,
  patchComment,
  onCommentLike,
  onReplyLike,
  infinite,
}) => {
  const hasPendingReplies = comments.some(
    (comment) => comment.replyCount > comment.replies.length,
  );

  const decorationUserIds = useMemo(() => {
    const ids: number[] = [];
    comments.forEach((comment) => {
      if (comment.accountId) ids.push(comment.accountId);
      comment.replies.forEach((reply) => {
        if (reply.accountId) ids.push(reply.accountId);
      });
    });
    return ids;
  }, [comments]);

  const { get } = useUserDecorations(decorationUserIds);

  return (
    <div className="comment-section__list">
      {comments.map((comment) => (
        <CommentItem
          key={comment.id}
          comment={comment}
          myAccountId={myAccountId}
          expanded={Boolean(expandedReplyIds[comment.id])}
          onToggleExpand={() => onToggleExpand(comment.id)}
          onLoadMoreReplies={() => onLoadMoreReplies?.(comment.id)}
          replyLoading={Boolean(replyLoadingIds?.[comment.id])}
          onLike={() => onCommentLike(comment.id)}
          onReply={() =>
            onOpenReply({
              commentId: comment.id,
              accountId: comment.accountId,
              nickname: comment.nickname,
            })
          }
          onReport={() => onReport('comment', comment.id)}
          onDelete={async () => {
            try {
              await deleteCommentApi(articleId, comment.id);
              onChange((prev) => prev.filter((c) => c.id !== comment.id));
            } catch (err) {
              message.error(formatApiError('删除评论失败', err));
            }
          }}
          onReplyLike={(replyId: string) => onReplyLike(comment.id, replyId)}
          onReplyToReply={(reply) =>
            onOpenReply({
              commentId: comment.id,
              accountId: reply.accountId,
              nickname: reply.nickname,
              afterReplyId: reply.id,
            })
          }
          onReplyReport={(reply) => onReport('reply', reply.id)}
          onReplyDelete={async (reply) => {
            try {
              await deleteReplyApi(articleId, comment.id, reply.id);
              patchComment(comment.id, {
                replies: comment.replies.filter((r) => r.id !== reply.id),
                replyCount: Math.max(0, comment.replyCount - 1),
              });
            } catch (err) {
              message.error(formatApiError('删除回复失败', err));
            }
          }}
          decoration={get(comment.accountId)}
          getReplyDecoration={get}
        />
      ))}
      {comments.length === 0 ? (
        <div className="comment-section__empty">还没有评论，来抢沙发</div>
      ) : null}
      {infinite ? (
        <ListEndHint
          ref={infinite.sentinelRef}
          loadingMore={infinite.loadingMore}
          hasMore={infinite.hasMore || hasPendingReplies}
          itemCount={comments.length}
        />
      ) : null}
    </div>
  );
};

export default memo(CommentList);
