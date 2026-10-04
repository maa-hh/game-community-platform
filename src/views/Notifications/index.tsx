import React, { useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Spin } from 'antd';

import NotificationCategoryPanel from '@/components/notifications/NotificationCategoryPanel';
import PageSubTopBar from '@/base-ui/PageSubTopBar';

import { useNotificationsPage } from './useNotificationsPage';

import './style.less';

function Notifications() {
  const navigate = useNavigate();
  const { panels, summaryLoading, toggleCategory, loadMore, handleNavigate } =
    useNotificationsPage();

  const goBack = useCallback(() => {
    if (window.history.length > 1) {
      navigate(-1);
      return;
    }
    navigate('/community');
  }, [navigate]);

  return (
    <div className="notifications-page">
      <div className="notifications-page__top-dock">
        <div className="notifications-page__align-track">
          <PageSubTopBar
            className="notifications-page__sub-top-bar"
            title="消息通知"
            onBack={goBack}
            extra={
              <span
                className="notifications-page__top-status"
                aria-live="polite"
              >
                {summaryLoading ? <Spin size="small" /> : null}
              </span>
            }
          />
        </div>
      </div>

      {panels.map((panel) => (
        <NotificationCategoryPanel
          key={panel.config.key}
          config={panel.config}
          unreadCount={panel.unreadCount}
          expanded={panel.expanded}
          messages={panel.messages}
          onToggle={() => void toggleCategory(panel.config.key)}
          onLoadMore={() => loadMore(panel.config.key)}
          onNavigate={handleNavigate}
        />
      ))}
    </div>
  );
}

export default Notifications;
