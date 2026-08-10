import React from 'react';
import type { FC } from 'react';
import { Badge, Spin } from 'antd';
import { DownOutlined, UpOutlined } from '@ant-design/icons';

import NotificationItemCard from '@/components/notifications/NotificationItemCard';
import type { NotificationCategoryConfig } from '@/views/Notifications/constants';
import type { INotificationMessage } from '@/types/notification';
import type { NotificationCategoryKey } from '@/types/notification';

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
              {messages.items.map((item) => (
                <NotificationItemCard
                  key={item.id}
                  item={item}
                  onNavigate={onNavigate}
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
