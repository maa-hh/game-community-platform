import React, { memo } from 'react';
import type { FC } from 'react';
import { DownOutlined, UpOutlined } from '@ant-design/icons';
import { Spin } from 'antd';

import CommentReply from '@/components/CommentReply';

import { REPLY_PAGE_SIZE, REPLY_PREVIEW_COUNT } from '../config';
import type { ICommentItemProps } from '../types';

const CommentItemReplies: FC<
  Pick<
    ICommentItemProps,
    | 'comment'
    | 'myAccountId'
    | 'expanded'
    | 'replyLoading'
    | 'onToggleExpand'
    | 'onLoadMoreReplies'
    | 'onReplyLike'
    | 'onReplyToReply'
    | 'onReplyReport'
    | 'onReplyDelete'
    | 'getReplyDecoration'
  >
> = ({
  comment,
  myAccountId,
  expanded,
  replyLoading,
  onToggleExpand,
  onLoadMoreReplies,
  onReplyLike,
  onReplyToReply,
  onReplyReport,
  onReplyDelete,
  getReplyDecoration,
}) => {
  if (comment.replyCount <= 0 && comment.replies.length === 0) return null;

  const loadedCount = comment.replies.length;
  const replyPageSize = comment.replyPageSize || REPLY_PAGE_SIZE;
  const totalReplies =
    loadedCount >= comment.replyCount ? loadedCount : comment.replyCount;
  const showAll = expanded || loadedCount <= REPLY_PREVIEW_COUNT;
  const visibleReplies = showAll
    ? comment.replies
    : comment.replies.slice(0, REPLY_PREVIEW_COUNT);
  const hasMoreReplies =
    (comment.replyPage || 0) * replyPageSize < comment.replyCount;
  const canExpand =
    !expanded &&
    (totalReplies > REPLY_PREVIEW_COUNT ||
      (loadedCount === 0 && comment.replyCount > 0));
  const canCollapse =
    expanded && !hasMoreReplies && loadedCount > REPLY_PREVIEW_COUNT;

  const renderAction = (
    label: string,
    onClick: () => void,
    icon: React.ReactNode,
  ) => (
    <button type="button" className="comment-item__expand" onClick={onClick}>
      <span className="comment-item__expand-inner">
        {icon}
        <span>{label}</span>
      </span>
    </button>
  );

  return (
    <div className="comment-item__replies">
      {visibleReplies.map((reply) => (
        <CommentReply
          key={reply.id}
          reply={reply}
          isMine={myAccountId === reply.accountId}
          onLike={() => onReplyLike(reply.id)}
          onReply={() => onReplyToReply(reply)}
          onReport={onReplyReport ? () => onReplyReport(reply) : undefined}
          onDelete={onReplyDelete ? () => onReplyDelete(reply) : undefined}
          decoration={getReplyDecoration?.(reply.accountId)}
        />
      ))}
      {replyLoading ? (
        <div className="comment-item__reply-loading">
          <Spin size="small" />
        </div>
      ) : null}
      {canExpand
        ? renderAction(
            loadedCount === 0
              ? `查看 ${comment.replyCount} 条回复`
              : `展开全部 ${totalReplies} 条回复`,
            onToggleExpand,
            <DownOutlined />,
          )
        : null}
      {expanded && hasMoreReplies && !replyLoading
        ? renderAction(
            '加载更多回复',
            () => onLoadMoreReplies?.(),
            <DownOutlined />,
          )
        : null}
      {canCollapse
        ? renderAction('收起回复', onToggleExpand, <UpOutlined />)
        : null}
    </div>
  );
};

export default memo(CommentItemReplies);
