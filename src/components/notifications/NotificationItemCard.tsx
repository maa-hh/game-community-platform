import React from 'react';
import type { FC } from 'react';

import NotificationCommentItem from '@/components/notifications/parts/NotificationCommentItem';
import NotificationFollowItem from '@/components/notifications/parts/NotificationFollowItem';
import NotificationLikeFavoriteItem from '@/components/notifications/parts/NotificationLikeFavoriteItem';
import NotificationSystemItem from '@/components/notifications/parts/NotificationSystemItem';
import type { INotificationMessage } from '@/types/notification';
import { buildNotificationLink } from '@/utils/notificationRoute';
import { resolveNotificationItemVariant } from '@/utils/notificationDisplay';

import './style.less';

interface NotificationItemCardProps {
  item: INotificationMessage;
  onNavigate?: (path: string) => void;
}

const NotificationItemCard: FC<NotificationItemCardProps> = ({
  item,
  onNavigate,
}) => {
  const variant = resolveNotificationItemVariant(item);
  const link = buildNotificationLink(item);

  const handleNavigate = () => {
    if (!link) return;
    onNavigate?.(link);
  };

  const handleCoverNavigate = () => {
    if (!link) return;
    onNavigate?.(link);
  };

  const isClickable = Boolean(link) && variant !== 'follow';

  return (
    <article
      className={`notification-item-card notification-item-card--${variant}${
        isClickable ? ' is-clickable' : ''
      }`}
      onClick={isClickable ? handleNavigate : undefined}
      onKeyDown={
        isClickable
          ? (event) => {
              if (event.key === 'Enter') handleNavigate();
            }
          : undefined
      }
      role={isClickable ? 'button' : undefined}
      tabIndex={isClickable ? 0 : undefined}
    >
      {variant === 'like_favorite' ? (
        <NotificationLikeFavoriteItem
          item={item}
          onCoverClick={link ? handleCoverNavigate : undefined}
        />
      ) : null}

      {variant === 'follow' ? <NotificationFollowItem item={item} /> : null}

      {variant === 'comment' ? (
        <NotificationCommentItem
          item={item}
          onCoverClick={link ? handleCoverNavigate : undefined}
        />
      ) : null}

      {variant === 'system' ? <NotificationSystemItem item={item} /> : null}
    </article>
  );
};

export default NotificationItemCard;
