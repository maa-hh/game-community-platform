import React, { memo } from 'react';
import type { FC } from 'react';

import { resolveAvatarFrameAsset } from '@/constants/avatarFrameCatalog';
import { commentFontStyle } from '@/utils/cosmeticAsset';

import ClampText from '@/base-ui/ClampText';
import ProfileUserLink from '@/components/ProfileUserLink';
import CommentOps from '@/components/CommentOps';

import type { IProps } from './types';

import './style.less';

const CommentReply: FC<IProps> = ({
  reply,
  isMine,
  onLike,
  onReply,
  onReport,
  onDelete,
  decoration,
}) => {
  const frameUrl = resolveAvatarFrameAsset(
    decoration?.avatarFrame?.code,
    decoration?.avatarFrame?.assetJson,
  )?.frameUrl;
  const mainStyle = commentFontStyle(decoration?.commentFont?.assetJson);
  const hasMainStyle = Boolean(mainStyle && Object.keys(mainStyle).length > 0);

  return (
    <div className="comment-reply" id={`reply-${reply.id}`}>
      <ProfileUserLink
        accountId={reply.accountId}
        nickname={reply.nickname}
        avatar={reply.avatar}
        avatarFrameUrl={frameUrl}
        size={28}
        showNickname={false}
        className="comment-reply__avatar"
      />
      <div
        className="comment-reply__main"
        style={hasMainStyle ? mainStyle : undefined}
      >
        <header className="comment-reply__meta">
          <ProfileUserLink
            accountId={reply.accountId}
            nickname={reply.nickname}
            avatar={reply.avatar}
            showAvatar={false}
            size={0}
          />
          {reply.replyToNickname && (
            <span className="comment-reply__at">
              回复{' '}
              <ProfileUserLink
                accountId={reply.replyToAccountId}
                nickname={reply.replyToNickname}
                showAvatar={false}
                size={0}
                className="comment-reply__at-user"
              />
            </span>
          )}
          <span>{reply.pending ? '发送中…' : reply.createdAt}</span>
        </header>
        <ClampText text={reply.content} />
        {reply.pending ? null : (
          <CommentOps
            liked={reply.liked}
            likeCount={reply.likeCount}
            likePending={reply.likePending}
            isMine={isMine}
            onLike={onLike}
            onReply={onReply}
            onReport={onReport}
            onDelete={onDelete}
            deleteTitle="删除回复？"
          />
        )}
      </div>
    </div>
  );
};

export default memo(CommentReply);
