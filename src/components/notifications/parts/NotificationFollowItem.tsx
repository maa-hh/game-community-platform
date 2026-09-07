import React from 'react';
import type { FC, MouseEvent } from 'react';
import { App } from 'antd';

import FollowButton from '@/components/FollowButton';
import ProfileUserLink from '@/components/ProfileUserLink';
import { toggleFollowByAccountApi } from '@/service/social';
import { resolveAvatarFrameAsset } from '@/constants/avatarFrameCatalog';
import type { INotificationMessage } from '@/types/notification';
import { getNotificationActionText } from '@/utils/notificationDisplay';
import { formatCardTime } from '@/utils/formatTime';
import { formatApiError } from '@/utils/apiError';
import { useOptimisticAction } from '@/hooks/useOptimisticAction';
import { useProfileFollowingSync } from '@/hooks/useProfileFollowingSync';
import { useDecorationRegistry } from '@/hooks/useDecorationRegistry';

interface NotificationFollowItemProps {
  item: INotificationMessage;
  followed?: boolean;
  onFollowedChange?: (accountId: number, followed: boolean) => void;
}

const NotificationFollowItem: FC<NotificationFollowItemProps> = ({
  item,
  followed = false,
  onFollowedChange,
}) => {
  const { message } = App.useApp();
  const actorAccountId = normalizeAccountId(item.actorAccountId);
  const decoration = useDecorationRegistry(actorAccountId);
  const avatarFrameUrl = resolveAvatarFrameAsset(
    decoration?.avatarFrame?.code,
    decoration?.avatarFrame?.assetJson,
  )?.frameUrl;
  const { run: runOptimisticAction, isPending } = useOptimisticAction();
  const syncProfileFollowing = useProfileFollowingSync();
  const actionText = getNotificationActionText(item.eventType);
  const timeText = item.createTime ? formatCardTime(item.createTime) : '';

  const handleFollowToggle = async (event: MouseEvent<HTMLElement>) => {
    event.preventDefault();
    event.stopPropagation();
    if (!actorAccountId || isPending('notification-follow')) return;

    const next = !followed;
    await runOptimisticAction('notification-follow', {
      apply: () => onFollowedChange?.(actorAccountId, next),
      request: () => toggleFollowByAccountApi(actorAccountId, next),
      commit: (res) => {
        onFollowedChange?.(actorAccountId, Boolean(res.data.followed));
        syncProfileFollowing();
      },
      rollback: (err) => {
        onFollowedChange?.(actorAccountId, !next);
        message.error(formatApiError('操作失败', err));
      },
    });
  };

  return (
    <div className="notification-item-card__row notification-item-card__row--follow">
      <div className="notification-item-card__lead">
        <ProfileUserLink
          accountId={actorAccountId}
          nickname={item.actorUsername || '玩家'}
          avatar={item.actorAvatar}
          avatarFrameUrl={avatarFrameUrl}
          size={40}
          showNickname={false}
        />
      </div>

      <div className="notification-item-card__body">
        <ProfileUserLink
          accountId={actorAccountId}
          nickname={item.actorUsername || '玩家'}
          avatar={item.actorAvatar}
          avatarFrameUrl={avatarFrameUrl}
          size={0}
          showAvatar={false}
          className="notification-item-card__nickname-link"
        />
        <p className="notification-item-card__action-line">
          <span>{actionText}</span>
          {timeText ? <time>{timeText}</time> : null}
        </p>
      </div>

      <FollowButton
        followed={followed}
        followText="回关"
        followedText="已关注"
        size="small"
        className={`notification-item-card__follow-btn${
          followed ? ' is-followed' : ''
        }`}
        disabled={isPending('notification-follow')}
        onClick={handleFollowToggle}
      />
    </div>
  );
};

function normalizeAccountId(value?: number): number | undefined {
  const accountId = Number(value);
  return Number.isInteger(accountId) && accountId > 0 ? accountId : undefined;
}

export default NotificationFollowItem;
