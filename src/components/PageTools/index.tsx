import React, { memo } from 'react';
import type { FC } from 'react';
import {
  LoadingOutlined,
  QuestionCircleOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { Tooltip } from 'antd';

import FeedbackModal from '@/components/FeedbackModal';
import { useRequireLogin } from '@/hooks/useRequireLogin';

import './style.less';

interface PageToolsProps {
  refreshing: boolean;
  onRefresh: () => void;
}

const PageTools: FC<PageToolsProps> = ({ refreshing, onRefresh }) => {
  const { requireLogin } = useRequireLogin();
  const [feedbackOpen, setFeedbackOpen] = React.useState(false);

  return (
    <>
      <div className="page-tools">
        <Tooltip title="刷新内容" placement="left">
          <button
            type="button"
            className={`page-tools__button${
              refreshing ? ' page-tools__button--refreshing' : ''
            }`}
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

        <Tooltip title="问题反馈" placement="left">
          <button
            type="button"
            className="page-tools__button"
            aria-label="问题反馈"
            onClick={() => {
              if (requireLogin()) setFeedbackOpen(true);
            }}
          >
            <QuestionCircleOutlined />
          </button>
        </Tooltip>
      </div>
      <FeedbackModal
        open={feedbackOpen}
        onClose={() => setFeedbackOpen(false)}
      />
    </>
  );
};

export default memo(PageTools);
