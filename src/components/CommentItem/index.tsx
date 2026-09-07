import React, { memo } from 'react';
import type { FC } from 'react';

import {
  commentCardStyle,
  commentCardTextTheme,
  commentFontStyle,
} from '@/utils/cosmeticAsset';
import { resolveAvatarFrameAsset } from '@/constants/avatarFrameCatalog';
import { resolveCommentCardAsset } from '@/constants/commentCardCatalog';
import ClampText from '@/base-ui/ClampText';
import ProfileUserLink from '@/components/ProfileUserLink';
import CommentOps from '@/components/CommentOps';

import CommentItemReplies from './parts/CommentItemReplies';
import type { ICommentItemProps } from './types';

import './style.less';

const CommentItem: FC<ICommentItemProps> = ({
  comment,
  myAccountId,
  expanded,
  onToggleExpand,
  onLoadMoreReplies,
  replyLoading,
  onLike,
  onReply,
  onReport,
  onDelete,
  onReplyLike,
  onReplyToReply,
  onReplyReport,
  onReplyDelete,
  decoration,
  getReplyDecoration,
  metaExtra,
}) => {
  const frameAsset = resolveAvatarFrameAsset(
    decoration?.avatarFrame?.code,
    decoration?.avatarFrame?.assetJson,
  );
  const commentCardAsset = resolveCommentCardAsset(
    decoration?.commentCard?.code,
    decoration?.commentCard?.assetJson,
  );
  const commentCardAssetJson = commentCardAsset
    ? JSON.stringify(commentCardAsset)
    : decoration?.commentCard?.assetJson;
  const hasCommentCard = Boolean(
    commentCardAsset?.bg || commentCardAsset?.border,
  );
  const textTheme = commentCardTextTheme(commentCardAssetJson);
  const mainStyle = {
    ...commentCardStyle(commentCardAssetJson),
    ...commentFontStyle(decoration?.commentFont?.assetJson),
  };
  const hasMainStyle = Object.keys(mainStyle).some(
    (key) => mainStyle[key as keyof typeof mainStyle] != null,
  );

  return (
    <article className="comment-item" id={`comment-${comment.id}`}>
      <ProfileUserLink
        accountId={comment.accountId}
        nickname={comment.nickname}
        avatar={comment.avatar}
        avatarFrameUrl={frameAsset?.frameUrl}
        size={36}
        showNickname={false}
        className="comment-item__avatar"
      />
      <div className="comment-item__main">
        <div
          className={[
            'comment-item__card',
            hasCommentCard ? 'comment-item__card--decorated' : '',
            textTheme ? `comment-item__card--text-${textTheme}` : '',
          ]
            .filter(Boolean)
            .join(' ')}
          style={hasMainStyle ? mainStyle : undefined}
        >
          <header className="comment-item__meta">
            <ProfileUserLink
              accountId={comment.accountId}
              nickname={comment.nickname}
              avatar={comment.avatar}
              showAvatar={false}
              size={0}
            />
            {metaExtra}
            <span>{comment.pending ? '发送中…' : comment.createdAt}</span>
          </header>
          <ClampText text={comment.content} />
          {comment.pending ? null : (
            <CommentOps
              liked={comment.liked}
              likeCount={comment.likeCount}
              likePending={comment.likePending}
              isMine={myAccountId === comment.accountId}
              onLike={onLike}
              onReply={onReply}
              onReport={onReport}
              onDelete={onDelete}
              deleteTitle="删除评论？"
            />
          )}
        </div>
        <CommentItemReplies
          comment={comment}
          myAccountId={myAccountId}
          expanded={expanded}
          replyLoading={replyLoading}
          onToggleExpand={onToggleExpand}
          onLoadMoreReplies={onLoadMoreReplies}
          onReplyLike={onReplyLike}
          onReplyToReply={onReplyToReply}
          onReplyReport={onReplyReport}
          onReplyDelete={onReplyDelete}
          getReplyDecoration={getReplyDecoration}
        />
      </div>
    </article>
  );
};

export default memo(CommentItem);
