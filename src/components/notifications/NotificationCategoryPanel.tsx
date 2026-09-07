import React, { useEffect, useMemo, useRef, useState } from 'react';
import type { FC } from 'react';
import { Badge, Spin } from 'antd';
import { DownOutlined, UpOutlined } from '@ant-design/icons';

import NotificationItemCard from '@/components/notifications/NotificationItemCard';
import type { NotificationCategoryConfig } from '@/views/Notifications/constants';
import type { INotificationMessage } from '@/types/notification';
import type { NotificationCategoryKey } from '@/types/notification';
import { NOTIFICATION_EVENT } from '@/types/notification';
import { checkFollowByAccountApi } from '@/service/social';

import './style.less';

interface CategoryMessagesState {
  items: INotificationMessage[];
  loading: boolean;
  loadingMore: boolean;
  hasMore: boolean;
  loaded: boolean;
  error?: boolean;
}

interface NotificationCategoryPanelProps {
  config: NotificationCategoryConfig;
  unreadCount: number;
  expanded: boolean;
  messages: CategoryMessagesState;
  onToggle: () => void;
  onLoadMore: () => void;
  onNavigate: (path: string) => void;
}

const NotificationCategoryPanel: FC<NotificationCategoryPanelProps> = ({
  config,
  unreadCount,
  expanded,
  messages,
  onToggle,
  onLoadMore,
  onNavigate,
}) => {
  const Icon = config.icon;
  const followActorIds = useMemo(
    () =>
      Array.from(
        new Set(
          messages.items
            .filter((item) => item.eventType === NOTIFICATION_EVENT.FOLLOW)
            .map((item) => item.actorAccountId)
            .filter(
              (accountId): accountId is number =>
                typeof accountId === 'number' &&
                Number.isInteger(accountId) &&
                accountId > 0,
            ),
        ),
      ),
    [messages.items],
  );
  const initialFollowStates = useMemo(
    () =>
      messages.items.reduce<Record<number, boolean>>((result, item) => {
        const accountId = item.actorAccountId;
        if (
          item.eventType === NOTIFICATION_EVENT.FOLLOW &&
          typeof accountId === 'number' &&
          Number.isInteger(accountId) &&
          accountId > 0
        ) {
          result[accountId] = Boolean(item.actorFollowed);
        }
        return result;
      }, {}),
    [messages.items],
  );
  const [followStates, setFollowStates] = useState<Record<number, boolean>>({});
  const followStateChangeVersionRef = useRef(0);

  useEffect(() => {
    if (followActorIds.length === 0) {
      setFollowStates({});
      return undefined;
    }

    let cancelled = false;
    const requestVersion = followStateChangeVersionRef.current;
    setFollowStates((current) => ({ ...initialFollowStates, ...current }));
    void Promise.all(
      followActorIds.map(async (accountId) => {
        try {
          const result = await checkFollowByAccountApi(accountId);
          return [accountId, Boolean(result.data.followed)] as const;
        } catch {
          return [accountId, undefined] as const;
        }
      }),
    ).then((results) => {
      if (cancelled || requestVersion !== followStateChangeVersionRef.current) {
        return;
      }
      setFollowStates((current) => {
        const next = { ...current };
        results.forEach(([accountId, followed]) => {
          if (followed !== undefined) next[accountId] = followed;
        });
        return next;
      });
    });

    return () => {
      cancelled = true;
    };
  }, [followActorIds, initialFollowStates]);

  const handleFollowStateChange = (accountId: number, followed: boolean) => {
    followStateChangeVersionRef.current += 1;
    setFollowStates((current) => ({ ...current, [accountId]: followed }));
  };

  return (
    <section className="notification-category-panel">
      <button
        type="button"
        className="notification-category-panel__header"
        onClick={onToggle}
      >
        <Badge dot={unreadCount > 0} offset={[-2, 2]}>
          <span className="notification-category-panel__icon">
            <Icon />
          </span>
        </Badge>
        <div className="notification-category-panel__meta">
          <h2 className="notification-category-panel__title">{config.label}</h2>
          <p className="notification-category-panel__desc">
            {config.description}
          </p>
        </div>
        <span className="notification-category-panel__caret">
          {expanded ? <UpOutlined /> : <DownOutlined />}
        </span>
      </button>

      {expanded ? (
        <div className="notification-category-panel__body">
          {messages.loading && !messages.loaded ? (
            <div className="notification-category-panel__empty">
              <Spin size="small" />
            </div>
          ) : messages.error ? (
            <div className="notification-category-panel__empty">
              加载失败，请收起后重试
            </div>
          ) : messages.items.length === 0 ? (
            <div className="notification-category-panel__empty">暂无通知</div>
          ) : (
            <>
              {messages.loading ? (
                <div
                  className="notification-category-panel__refreshing"
                  role="status"
                  aria-label="正在刷新通知"
                >
                  <Spin size="small" />
                  <span>正在更新</span>
                </div>
              ) : null}
              {messages.items.map((item) => (
                <NotificationItemCard
                  key={item.id}
                  item={item}
                  onNavigate={onNavigate}
                  followed={
                    item.actorAccountId
                      ? (followStates[item.actorAccountId] ??
                        initialFollowStates[item.actorAccountId] ??
                        Boolean(item.actorFollowed))
                      : false
                  }
                  onFollowedChange={handleFollowStateChange}
                />
              ))}
              {messages.hasMore ? (
                <button
                  type="button"
                  className="notification-category-panel__more"
                  disabled={messages.loadingMore}
                  onClick={onLoadMore}
                >
                  {messages.loadingMore ? '加载中…' : '加载更多'}
                </button>
              ) : null}
            </>
          )}
        </div>
      ) : null}
    </section>
  );
};

export default NotificationCategoryPanel;

export type { NotificationCategoryKey };
