import React, { useState } from 'react';
import type { FC, MouseEvent } from 'react';
import { App } from 'antd';

import ProfileUserLink from '@/components/ProfileUserLink';
import StatAction from '@/base-ui/StatAction';
import ReplyPopup from '@/components/ReplyPopup';
import NotificationPostCover from '@/components/notifications/parts/NotificationPostCover';
import {
  createReplyApi,
  toggleCommentLikeApi,
  toggleReplyLikeApi,
} from '@/service/social';
import type { INotificationMessage } from '@/types/notification';
import { NOTIFICATION_EVENT } from '@/types/notification';
import {
  getNotificationActionText,
  resolveNotificationContent,
  resolveNotificationCoverTitle,
  resolveNotificationQuote,
} from '@/utils/notificationDisplay';
import { formatCardTime } from '@/utils/formatTime';
import { useAppSelector } from '@/store';
import { formatApiError } from '@/utils/apiError';

interface NotificationCommentItemProps {
  item: INotificationMessage;
  onCoverClick?: () => void;
}

const NotificationCommentItem: FC<NotificationCommentItemProps> = ({
  item,
  onCoverClick,
}) => {
  const { message } = App.useApp();
  const { user } = useAppSelector((state) => state.auth);
  const [liked, setLiked] = useState(false);
  const [likeCount, setLikeCount] = useState(0);
  const [replyOpen, setReplyOpen] = useState(false);
  const [replyLoading, setReplyLoading] = useState(false);

  const actionText = getNotificationActionText(item.eventType);
  const timeText = item.createTime ? formatCardTime(item.createTime) : '';
  const contentText = resolveNotificationContent(item);
  const quoteText = resolveNotificationQuote(item);
  const isDanmakuNotification =
    item.eventType === NOTIFICATION_EVENT.DANMAKU_COMMENT;

  const handleCoverClick = (event: MouseEvent<HTMLButtonElement>) => {
    event.stopPropagation();
    onCoverClick?.();
  };

  const handleLike = async (event: MouseEvent<HTMLButtonElement>) => {
    event.stopPropagation();
    if (!item.articlePublicId) return;
    const articleId = item.articlePublicId;
    const next = !liked;
    try {
      if (item.eventType === NOTIFICATION_EVENT.COMMENT_REPLY && item.replyId) {
        const res = await toggleReplyLikeApi(
          articleId,
          String(item.commentId || ''),
          String(item.replyId),
          next,
        );
        setLiked(res.data.liked);
        setLikeCount(res.data.likeCount);
      } else if (item.commentId) {
        const res = await toggleCommentLikeApi(
          articleId,
          String(item.commentId),
          next,
        );
        setLiked(res.data.liked);
        setLikeCount(res.data.likeCount);
      }
    } catch (err) {
      message.error(formatApiError('操作失败', err));
    }
  };

  const handleReplySubmit = async (content: string) => {
    if (!user?.accountId || !item.articlePublicId || !item.commentId) return;
    setReplyLoading(true);
    try {
      await createReplyApi(
        item.articlePublicId,
        String(item.commentId),
        content,
        {
          accountId: item.actorAccountId || 0,
          nickname: item.actorUsername || '用户',
        },
        {
          accountId: user.accountId,
          nickname: user.username || '我',
          avatar: user.avatar,
        },
        item.replyId ? String(item.replyId) : undefined,
      );
      message.success('回复成功');
      setReplyOpen(false);
    } catch (err) {
      message.error(formatApiError('回复失败', err));
    } finally {
      setReplyLoading(false);
    }
  };

  return (
    <>
      <div className="notification-item-card__row notification-item-card__row--comment">
        <div className="notification-item-card__lead">
          <ProfileUserLink
            accountId={item.actorAccountId}
            nickname={item.actorUsername || '玩家'}
            avatar={item.actorAvatar}
            size={40}
            showNickname={false}
          />
        </div>

        <div className="notification-item-card__body">
          <div className="notification-item-card__main">
            <div className="notification-item-card__text">
              <ProfileUserLink
                accountId={item.actorAccountId}
                nickname={item.actorUsername || '玩家'}
                avatar={item.actorAvatar}
                size={0}
                showAvatar={false}
                className="notification-item-card__nickname-link"
              />
              <p className="notification-item-card__action-line">
                <span>{actionText}</span>
                {timeText ? <time>{timeText}</time> : null}
              </p>

              {contentText ? (
                <p className="notification-item-card__content">{contentText}</p>
              ) : null}

              {quoteText ? (
                <p className="notification-item-card__quote">{quoteText}</p>
              ) : null}

              {!isDanmakuNotification ? (
                <footer className="notification-item-card__actions">
                  <StatAction
                    kind="reply"
                    size="sm"
                    stopPropagation
                    onClick={(event) => {
                      event.stopPropagation();
                      setReplyOpen(true);
                    }}
                  />
                  <StatAction
                    kind="like"
                    count={likeCount}
                    active={liked}
                    size="sm"
                    stopPropagation
                    onClick={handleLike}
                  />
                </footer>
              ) : null}
            </div>

            <NotificationPostCover
              coverSource={item.articleCoverSource}
              coverUrl={item.articleCoverUrl}
              title={resolveNotificationCoverTitle(item)}
              onClick={onCoverClick ? handleCoverClick : undefined}
            />
          </div>
        </div>
      </div>

      {!isDanmakuNotification ? (
        <ReplyPopup
          open={replyOpen}
          nickname={item.actorUsername || '用户'}
          loading={replyLoading}
          onClose={() => setReplyOpen(false)}
          onSubmit={handleReplySubmit}
        />
      ) : null}
    </>
  );
};

export default NotificationCommentItem;
