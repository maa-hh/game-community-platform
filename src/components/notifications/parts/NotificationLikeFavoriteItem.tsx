import React from 'react';
import type { FC, MouseEvent } from 'react';

import ProfileUserLink from '@/components/ProfileUserLink';
import AggregateActorGroup from '@/components/notifications/AggregateActorGroup';
import NotificationPostCover from '@/components/notifications/parts/NotificationPostCover';
import type { INotificationMessage } from '@/types/notification';
import {
  getNotificationActionText,
  resolveNotificationCoverTitle,
} from '@/utils/notificationDisplay';
import { formatCardTime } from '@/utils/formatTime';

interface NotificationLikeFavoriteItemProps {
  item: INotificationMessage;
  onCoverClick?: () => void;
}

const NotificationLikeFavoriteItem: FC<NotificationLikeFavoriteItemProps> = ({
  item,
  onCoverClick,
}) => {
  const isAggregated = Boolean(item.aggregated && item.aggregateActors?.length);
  const aggregateAction =
    item.aggregateHasLike && item.aggregateHasFavorite
      ? 'mixed'
      : item.aggregateActors?.find((actor) => actor.action)?.action;
  const actionText = getNotificationActionText(
    item.eventType,
    isAggregated,
    aggregateAction,
  );
  const timeText = item.createTime ? formatCardTime(item.createTime) : '';

  const handleCoverClick = (event: MouseEvent<HTMLButtonElement>) => {
    event.stopPropagation();
    onCoverClick?.();
  };

  return (
    <div className="notification-item-card__row notification-item-card__row--like">
      <div className="notification-item-card__lead">
        {isAggregated ? (
          <AggregateActorGroup
            actors={item.aggregateActors || []}
            total={item.aggregateTotal}
          />
        ) : (
          <ProfileUserLink
            accountId={item.actorAccountId}
            nickname={item.actorUsername || '玩家'}
            avatar={item.actorAvatar}
            size={40}
            showNickname={false}
          />
        )}
      </div>

      <div className="notification-item-card__body">
        {isAggregated ? (
          <p className="notification-item-card__nickname">
            {item.aggregateTotal && item.aggregateTotal > 1
              ? `等 ${item.aggregateTotal} 人`
              : item.aggregateActors?.[0]?.username || '玩家'}
          </p>
        ) : (
          <ProfileUserLink
            accountId={item.actorAccountId}
            nickname={item.actorUsername || '玩家'}
            avatar={item.actorAvatar}
            size={0}
            showAvatar={false}
            className="notification-item-card__nickname-link"
          />
        )}

        <p className="notification-item-card__action-line">
          <span>{actionText}</span>
          {timeText ? <time>{timeText}</time> : null}
        </p>
      </div>

      <NotificationPostCover
        coverSource={item.articleCoverSource}
        coverUrl={item.articleCoverUrl}
        title={resolveNotificationCoverTitle(item)}
        onClick={onCoverClick ? handleCoverClick : undefined}
      />
    </div>
  );
};

export default NotificationLikeFavoriteItem;
