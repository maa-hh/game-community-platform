import React, { memo } from 'react';
import type { FC } from 'react';
import { Progress } from 'antd';

import { articleStatusLabel } from '@/service/content';

import { useArticleProgressPoll } from './useArticleProgressPoll';

import './style.less';

const ArticleProgressBanner: FC = () => {
  const poll = useArticleProgressPoll();

  if (!poll.visible) return null;

  return (
    <div className="article-progress-banner" role="status" aria-live="polite">
      <div className="article-progress-banner__inner">
        <div className="article-progress-banner__meta">
          <span className="article-progress-banner__title">
            《{poll.title}》{articleStatusLabel(poll.status)}
          </span>
          <span className="article-progress-banner__stage">{poll.stage}</span>
        </div>
        {poll.showUploadPercent ? (
          <Progress
            className="article-progress-banner__bar"
            percent={poll.uploadPercent}
            showInfo
            status="active"
            strokeColor="var(--color-primary)"
            size="small"
          />
        ) : poll.showAuditWaiting ? (
          <div
            className="article-progress-banner__bar article-progress-banner__bar--audit"
            aria-hidden
          />
        ) : null}
      </div>
    </div>
  );
};

export default memo(ArticleProgressBanner);
