import React, { useEffect, useRef, useState } from 'react';
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
import {
  addGameReviewReplyApi,
  likeGameReviewReplyApi,
  unlikeGameReviewReplyApi,
} from '@/service/game';
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
import { useOptimisticAction } from '@/hooks/useOptimisticAction';
import { invalidatePageDataCache } from '@/hooks/pageDataCache';
import { invalidateProfileDataCaches } from '@/utils/profileDataCache';
import { PROFILE_DATA_DOMAIN } from '@/types/profileRealtime';

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
  const [liked, setLiked] = useState(Boolean(item.liked));
  const [likeCount, setLikeCount] = useState(item.likeCount ?? 0);
  const [replyOpen, setReplyOpen] = useState(false);
  const [sentReply, setSentReply] = useState<{
    content: string;
    pending: boolean;
  } | null>(null);
  const { run: runOptimisticAction, isPending } = useOptimisticAction();
  const interactionSyncRef = useRef({ itemId: item.id, optimistic: false });

  // 通知刷新后同一个卡片实例可能复用，服务端补全的值需要同步进来；
  // 但本地 optimistic 请求期间不能被一次旧的通知刷新覆盖。
  useEffect(() => {
    if (
      interactionSyncRef.current.itemId === item.id &&
      interactionSyncRef.current.optimistic
    ) {
      return;
    }
    interactionSyncRef.current = { itemId: item.id, optimistic: false };
    setLiked(Boolean(item.liked));
    setLikeCount(item.likeCount ?? 0);
  }, [item.id, item.liked, item.likeCount]);

  const actionText = getNotificationActionText(item.eventType);
  const timeText = item.createTime ? formatCardTime(item.createTime) : '';
  const contentText = resolveNotificationContent(item);
  const quoteText = resolveNotificationQuote(item);
  const isDanmakuNotification =
    item.eventType === NOTIFICATION_EVENT.DANMAKU_COMMENT;
  const replyTargetAccountId =
    item.actorAccountId && item.actorAccountId > 0
      ? item.actorAccountId
      : undefined;
  const isGameReviewReply =
    item.eventType === NOTIFICATION_EVENT.GAME_REVIEW_REPLY;

  const handleCoverClick = (event: MouseEvent<HTMLButtonElement>) => {
    event.stopPropagation();
    onCoverClick?.();
  };

  const handleLike = async (event: MouseEvent<HTMLButtonElement>) => {
    event.stopPropagation();
    if (!item.articlePublicId && !item.gameReviewReplyId) return;
    const articleId = item.articlePublicId;
    if (isPending('notification-like')) return;
    const snapshot = { liked, likeCount };
    const next = !liked;
    await runOptimisticAction('notification-like', {
      apply: () => {
        interactionSyncRef.current = { itemId: item.id, optimistic: true };
        setLiked(next);
        setLikeCount(Math.max(0, likeCount + (next ? 1 : -1)));
      },
      request: async (): Promise<{ liked: boolean; likeCount: number }> => {
        if (isGameReviewReply && item.gameReviewReplyId) {
          await (next
            ? likeGameReviewReplyApi(item.gameReviewReplyId)
            : unlikeGameReviewReplyApi(item.gameReviewReplyId));
          return {
            liked: next,
            likeCount: Math.max(0, snapshot.likeCount + (next ? 1 : -1)),
          };
        }
        if (
          item.eventType === NOTIFICATION_EVENT.COMMENT_REPLY &&
          item.replyId
        ) {
          if (!articleId) return Promise.reject(new Error('互动目标不存在'));
          const response = await toggleReplyLikeApi(
            articleId,
            String(item.commentId || ''),
            String(item.replyId),
            next,
            snapshot.likeCount,
          );
          return response.data;
        }
        if (item.commentId) {
          if (!articleId) return Promise.reject(new Error('互动目标不存在'));
          const response = await toggleCommentLikeApi(
            articleId,
            String(item.commentId),
            next,
          );
          return response.data;
        }
        return Promise.reject(new Error('互动目标不存在'));
      },
      commit: (res) => {
        interactionSyncRef.current.optimistic = false;
        setLiked(res.liked);
        setLikeCount(res.likeCount);
        if (articleId) {
          invalidatePageDataCache(`post-comments:${articleId}`);
        }
        if (isGameReviewReply && item.gameAppId && user?.accountId != null) {
          invalidatePageDataCache(`${item.gameAppId}:${user.accountId}`);
        }
      },
      rollback: (err) => {
        interactionSyncRef.current.optimistic = false;
        setLiked(snapshot.liked);
        setLikeCount(snapshot.likeCount);
        message.error(formatApiError('操作失败', err));
      },
    });
  };

  const handleReplySubmit = async (content: string) => {
    if (!user?.accountId || (!item.articlePublicId && !item.gameReviewId)) {
      return;
    }
    if (!replyTargetAccountId) {
      message.warning('回复对象信息缺失，请刷新消息后重试');
      setReplyOpen(false);
      return;
    }
    if (isPending('notification-reply')) return;
    setReplyOpen(false);
    await runOptimisticAction('notification-reply', {
      apply: () => setSentReply({ content, pending: true }),
      request: async (): Promise<void> => {
        if (isGameReviewReply && item.gameReviewId) {
          await addGameReviewReplyApi(item.gameReviewId, content, {
            replyToReplyId: item.gameReviewReplyId,
          });
          return;
        }
        if (!item.articlePublicId || !item.commentId) {
          return Promise.reject(new Error('回复对象信息缺失'));
        }
        await createReplyApi(
          item.articlePublicId,
          String(item.commentId),
          content,
          {
            accountId: replyTargetAccountId!,
            nickname: item.actorUsername || '用户',
          },
          {
            accountId: user.accountId,
            nickname: user.username || '我',
            avatar: user.avatar,
          },
          item.replyId ? String(item.replyId) : undefined,
        );
        return;
      },
      commit: () => {
        setSentReply((current) =>
          current ? { ...current, pending: false } : current,
        );
        if (item.articlePublicId) {
          invalidatePageDataCache(`post-comments:${item.articlePublicId}`);
          invalidateProfileDataCaches(user.accountId, [
            PROFILE_DATA_DOMAIN.COMMENTS,
          ]);
        }
        if (isGameReviewReply && item.gameAppId) {
          invalidatePageDataCache(`${item.gameAppId}:${user.accountId}`);
        }
        message.success('回复成功');
      },
      rollback: (err) => {
        setSentReply(null);
        message.error(formatApiError('回复失败', err));
      },
    });
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

              {sentReply ? (
                <p className="notification-item-card__reply-preview">
                  我回复：{sentReply.content}
                  {sentReply.pending ? '（发送中…）' : ''}
                </p>
              ) : null}

              {!isDanmakuNotification ? (
                <footer className="notification-item-card__actions">
                  <StatAction
                    kind="reply"
                    size="sm"
                    stopPropagation
                    onClick={(event) => {
                      event.stopPropagation();
                      if (!replyTargetAccountId) {
                        message.warning('回复对象信息缺失，请刷新消息后重试');
                        return;
                      }
                      setReplyOpen(true);
                    }}
                  />
                  <StatAction
                    kind="like"
                    count={likeCount}
                    active={liked}
                    disabled={isPending('notification-like')}
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
              gameAppId={item.gameAppId}
              gameCoverUrl={item.gameCoverUrl}
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
          loading={isPending('notification-reply')}
          onClose={() => setReplyOpen(false)}
          onSubmit={handleReplySubmit}
        />
      ) : null}
    </>
  );
};

export default NotificationCommentItem;
