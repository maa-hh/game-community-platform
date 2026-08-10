import React, { memo } from 'react';
import type { FC } from 'react';
import { LoadingOutlined, ReloadOutlined } from '@ant-design/icons';
import { Tooltip } from 'antd';

interface FeedPanelRailProps {
  refreshing: boolean;
  loading: boolean;
  showRefresh: boolean;
  onRefresh: () => void;
  onScrollToTop: () => void;
}

const FeedPanelRail: FC<FeedPanelRailProps> = ({
  refreshing,
  loading,
  showRefresh,
  onRefresh,
  onScrollToTop,
}) => (
  <div className="feed-panel__rail" onClick={(e) => e.stopPropagation()}>
    {showRefresh && (
      <Tooltip title="刷新内容" placement="left">
        <button
          type="button"
          className="feed-panel__tool"
          aria-label="刷新内容"
          disabled={refreshing || loading}
          onClick={() => void onRefresh()}
        >
          {refreshing ? <LoadingOutlined spin /> : <ReloadOutlined />}
        </button>
      </Tooltip>
    )}
    <Tooltip title="回到顶部" placement="left">
      <button
        type="button"
        className="feed-panel__tool feed-panel__tool--top"
        aria-label="回到顶部"
        onClick={onScrollToTop}
      >
        <span className="feed-panel__tool-icon" aria-hidden>
          ▲
        </span>
        <span className="feed-panel__tool-text">顶部</span>
      </button>
    </Tooltip>
  </div>
);

export default memo(FeedPanelRail);
