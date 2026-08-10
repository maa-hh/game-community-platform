import React, { useEffect, useState } from 'react';
import type { FC, MouseEvent } from 'react';
import { message } from 'antd';

import ProfileUserLink from '@/components/ProfileUserLink';
import {
  checkFollowByAccountApi,
  toggleFollowByAccountApi,
} from '@/service/social';
import type { INotificationMessage } from '@/types/notification';
import { getNotificationActionText } from '@/utils/notificationDisplay';
import { formatCardTime } from '@/utils/formatTime';
import { formatApiError } from '@/utils/apiError';

interface NotificationFollowItemProps {
  item: INotificationMessage;
}

const NotificationFollowItem: FC<NotificationFollowItemProps> = ({ item }) => {
  const [followed, setFollowed] = useState(Boolean(item.actorFollowed));
  const [submitting, setSubmitting] = useState(false);
  const actionText = getNotificationActionText(item.eventType);
  const timeText = item.createTime ? formatCardTime(item.createTime) : '';
  const actorAccountId = item.actorAccountId;

  useEffect(() => {
    if (!actorAccountId) return undefined;

    let cancelled = false;
    void checkFollowByAccountApi(actorAccountId)
      .then((res) => {
        if (!cancelled) setFollowed(res.data.followed);
      })
      .catch(() => undefined);

    return () => {
      cancelled = true;
    };
  }, [actorAccountId]);

  const handleFollowToggle = async (event: MouseEvent<HTMLButtonElement>) => {
    event.stopPropagation();
    if (!actorAccountId || submitting) return;

    const next = !followed;
    setSubmitting(true);
    try {
      await toggleFollowByAccountApi(actorAccountId, next);
      setFollowed(next);
    } catch (err) {
      message.error(formatApiError('操作失败', err));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="notification-item-card__row notification-item-card__row--follow">
      <div className="notification-item-card__lead">
        <ProfileUserLink
          accountId={actorAccountId}
          nickname={item.actorUsername || '玩家'}
          avatar={item.actorAvatar}
          size={40}
          showNickname={false}
        />
      </div>

      <div className="notification-item-card__body">
        <ProfileUserLink
          accountId={actorAccountId}
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
      </div>

      <button
        type="button"
        className={`notification-item-card__follow-btn${
          followed ? ' is-followed' : ''
        }`}
        disabled={submitting}
        onClick={handleFollowToggle}
      >
        {followed ? '已关注' : '回关'}
      </button>
    </div>
  );
};

export default NotificationFollowItem;
