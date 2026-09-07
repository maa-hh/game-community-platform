import React, { Children, memo, useEffect, useRef } from 'react';
import type { FC } from 'react';
import { Spin } from 'antd';

import ListEndHint from '@/base-ui/ListEndHint';
import { PAGE_REFRESH_EVENT, type PageRefreshEvent } from '@/utils/pageRefresh';

import type { IFeedPanelProps } from './types';
import { useFeedPanel } from './useFeedPanel';

import './style.less';

const FeedPanel: FC<IFeedPanelProps> = ({
  children,
  masonry,
  loading = false,
  refreshing = false,
  onRefresh,
  className,
  empty,
  infinite,
  listLayout = 'stack',
}) => {
  const panelRef = useRef<HTMLElement>(null);
  const { handleRefresh } = useFeedPanel(onRefresh);
  const childCount = Children.count(children);
  const hasList = childCount > 0 || Boolean(masonry);
  const isRefreshing = refreshing || (loading && hasList);
  const itemCount = infinite?.itemCount ?? childCount;
  const isMasonry = listLayout === 'masonry';

  useEffect(() => {
    if (!onRefresh) return undefined;

    const refreshVisiblePanel = (event: Event) => {
      const panel = panelRef.current;
      if (
        !panel ||
        panel.getClientRects().length === 0 ||
        getComputedStyle(panel).visibility !== 'visible'
      )
        return;
      event.preventDefault();
      const task = handleRefresh();
      (event as PageRefreshEvent).detail?.track(task);
    };

    window.addEventListener(PAGE_REFRESH_EVENT, refreshVisiblePanel);
    return () => {
      window.removeEventListener(PAGE_REFRESH_EVENT, refreshVisiblePanel);
    };
  }, [handleRefresh, onRefresh]);

  return (
    <section
      ref={panelRef}
      className={`feed-panel${className ? ` ${className}` : ''}${
        isMasonry ? ' feed-panel--masonry' : ''
      }`}
      aria-busy={loading || refreshing}
    >
      {loading && !hasList ? (
        <div className="feed-panel__loading">
          <Spin />
        </div>
      ) : empty && !hasList ? (
        <div className="feed-panel__empty">{empty}</div>
      ) : (
        <div className="feed-panel__list">
          {isRefreshing ? (
            <div
              className="feed-panel__refreshing"
              role="status"
              aria-label="正在刷新"
            >
              <Spin size="small" />
            </div>
          ) : null}
          {isMasonry ? (masonry ?? children) : children}
          {infinite?.loadingMore && isMasonry ? (
            <div className="feed-panel__loading-more" role="status">
              <Spin size="small" />
              <span>正在加载更多</span>
            </div>
          ) : null}
          {infinite ? (
            <ListEndHint
              ref={infinite.sentinelRef}
              loadingMore={isMasonry ? false : infinite.loadingMore}
              hasMore={infinite.hasMore}
              itemCount={itemCount}
            />
          ) : null}
        </div>
      )}
    </section>
  );
};

export default memo(FeedPanel);
