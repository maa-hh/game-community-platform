import React from 'react';
import type { FC } from 'react';

import ProfileUserLink from '@/components/ProfileUserLink';
import AggregateActorGroup from '@/components/notifications/AggregateActorGroup';
import type { INotificationMessage } from '@/types/notification';
import { formatCardTime } from '@/utils/formatTime';

interface NotificationSystemItemProps {
  item: INotificationMessage;
}

const NotificationSystemItem: FC<NotificationSystemItemProps> = ({ item }) => {
  const isAggregated = Boolean(item.aggregated && item.aggregateActors?.length);
  const timeText = item.createTime ? formatCardTime(item.createTime) : '';

  return (
    <div className="notification-item-card__row notification-item-card__row--system">
      <div className="notification-item-card__body notification-item-card__body--full">
        {isAggregated ? (
          <AggregateActorGroup
            actors={item.aggregateActors || []}
            total={item.aggregateTotal}
          />
        ) : item.actorAccountId && item.actorAccountId > 0 ? (
          <ProfileUserLink
            accountId={item.actorAccountId}
            nickname={item.actorUsername || '玩家'}
            avatar={item.actorAvatar}
            size={32}
          />
        ) : null}

        <p className="notification-item-card__preview">
          {item.previewText || item.resultText || '系统通知'}
        </p>

        {timeText ? (
          <p className="notification-item-card__action-line">
            <time>{timeText}</time>
          </p>
        ) : null}
      </div>
    </div>
  );
};

export default NotificationSystemItem;
