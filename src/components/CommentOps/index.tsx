import React, { memo } from 'react';
import type { FC } from 'react';
import { App } from 'antd';
import { DeleteOutlined, FlagOutlined } from '@ant-design/icons';

import StatAction from '@/base-ui/StatAction';

import type { CommentOpsProps } from './types';

import './style.less';

/** 评论/回复操作条：赞 · 回复 · 举报 · 删除（差异仅数据） */
const CommentOps: FC<CommentOpsProps> = ({
  liked,
  likeCount,
  likePending,
  isMine,
  size = 'sm',
  onLike,
  onReply,
  onReport,
  onDelete,
  deleteTitle = '删除？',
}) => {
  const { modal } = App.useApp();
  return (
    <div className="comment-ops">
      <StatAction
        kind="like"
        size={size}
        count={likeCount}
        active={liked}
        disabled={likePending}
        stopPropagation
        onClick={onLike}
      />
      <StatAction kind="reply" size={size} onClick={onReply} />
      {!isMine && onReport && (
        <button
          type="button"
          className="comment-ops__icon"
          aria-label="举报"
          onClick={onReport}
        >
          <FlagOutlined />
        </button>
      )}
      {isMine && onDelete && (
        <button
          type="button"
          className="comment-ops__text"
          onClick={() => {
            modal.confirm({ title: deleteTitle, onOk: onDelete });
          }}
        >
          <DeleteOutlined />
        </button>
      )}
    </div>
  );
};

export default memo(CommentOps);

export type { CommentOpsProps } from './types';
