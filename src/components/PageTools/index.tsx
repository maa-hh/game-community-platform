import React, { memo } from 'react';
import type { FC } from 'react';
import { LoadingOutlined, ReloadOutlined } from '@ant-design/icons';
import { Tooltip } from 'antd';

import './style.less';

interface PageToolsProps {
  refreshing: boolean;
  onRefresh: () => void;
}

const PageTools: FC<PageToolsProps> = ({ refreshing, onRefresh }) => (
  <div className="page-tools">
    <Tooltip title="刷新内容" placement="left">
      <button
        type="button"
        className="page-tools__button"
        aria-label="刷新内容"
        disabled={refreshing}
        onClick={onRefresh}
      >
        {refreshing ? <LoadingOutlined spin /> : <ReloadOutlined />}
      </button>
    </Tooltip>

    <Tooltip title="回到顶部" placement="left">
      <button
        type="button"
        className="page-tools__button page-tools__button--top"
        aria-label="回到顶部"
        onClick={() => window.scrollTo({ top: 0, behavior: 'smooth' })}
      >
        <span className="page-tools__icon" aria-hidden>
          ▲
        </span>
        <span className="page-tools__text">顶部</span>
      </button>
    </Tooltip>
  </div>
);

export default memo(PageTools);
