import React from 'react';
import type { FC, KeyboardEvent, MouseEvent } from 'react';

import NotificationCommentItem from '@/components/notifications/parts/NotificationCommentItem';
import NotificationFollowItem from '@/components/notifications/parts/NotificationFollowItem';
import NotificationLikeFavoriteItem from '@/components/notifications/parts/NotificationLikeFavoriteItem';
import NotificationSystemItem from '@/components/notifications/parts/NotificationSystemItem';
import type { INotificationMessage } from '@/types/notification';
import { buildNotificationLink } from '@/utils/notificationRoute';
import { resolveNotificationItemVariant } from '@/utils/notificationDisplay';
import { preloadGameDetail } from '@/router/preload';

import './style.less';

interface NotificationItemCardProps {
  item: INotificationMessage;
  onNavigate?: (path: string) => void;
  followed?: boolean;
  onFollowedChange?: (accountId: number, followed: boolean) => void;
}

/** 卡片可点击时，内部按钮/链接应保留自己的交互，不触发卡片跳转。 */
function isNestedInteractiveTarget(
  target: EventTarget | null,
  currentTarget: EventTarget,
): boolean {
  if (!(target instanceof Element)) return false;

  const interactiveTarget = target.closest(
    'button, a, input, textarea, select, [role="button"]',
  );
  return Boolean(interactiveTarget && interactiveTarget !== currentTarget);
}

const NotificationItemCard: FC<NotificationItemCardProps> = ({
  item,
  onNavigate,
  followed,
  onFollowedChange,
}) => {
  const variant = resolveNotificationItemVariant(item);
  const link = buildNotificationLink(item);

  const handleNavigate = (
    event?: MouseEvent<HTMLElement> | KeyboardEvent<HTMLElement>,
  ) => {
    if (event && isNestedInteractiveTarget(event.target, event.currentTarget)) {
      return;
    }
    if (!link) return;
    onNavigate?.(link);
  };

  const handleCoverNavigate = () => {
    if (!link) return;
    onNavigate?.(link);
  };

  const isClickable = Boolean(link) && variant !== 'follow';
  const isGameLink = link?.startsWith('/game/') ?? false;

  return (
    <article
      className={`notification-item-card notification-item-card--${variant}${
        isClickable ? ' is-clickable' : ''
      }`}
      onClick={isClickable ? handleNavigate : undefined}
      onKeyDown={
        isClickable
          ? (event) => {
              if (event.key === 'Enter') handleNavigate(event);
            }
          : undefined
      }
      onMouseEnter={isGameLink ? () => void preloadGameDetail() : undefined}
      onPointerDown={isGameLink ? () => void preloadGameDetail() : undefined}
      role={isClickable ? 'button' : undefined}
      tabIndex={isClickable ? 0 : undefined}
    >
      {variant === 'like_favorite' ? (
        <NotificationLikeFavoriteItem
          item={item}
          onCoverClick={link ? handleCoverNavigate : undefined}
        />
      ) : null}

      {variant === 'follow' ? (
        <NotificationFollowItem
          item={item}
          followed={followed}
          onFollowedChange={onFollowedChange}
        />
      ) : null}

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
