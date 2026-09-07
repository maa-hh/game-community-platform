import React, {
  memo,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
} from 'react';
import type { FC } from 'react';
import { Progress } from 'antd';

import { articleStatusLabel } from '@/service/content';

import { useArticleProgressPoll } from './useArticleProgressPoll';

import './style.less';

const ArticleProgressBanner: FC = () => {
  const poll = useArticleProgressPoll();
  const [expanded, setExpanded] = useState(false);
  const [bannerHeight, setBannerHeight] = useState(0);
  const bannerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (poll.visible && poll.items.length <= 3) {
      setExpanded(false);
    }
  }, [poll.items.length, poll.visible]);

  useLayoutEffect(() => {
    if (!poll.visible) {
      setBannerHeight(0);
      return undefined;
    }

    const banner = bannerRef.current;
    if (!banner) return undefined;

    const updateHeight = () => {
      setBannerHeight(banner.getBoundingClientRect().height);
    };
    updateHeight();

    if (typeof ResizeObserver === 'undefined') return undefined;
    const observer = new ResizeObserver(updateHeight);
    observer.observe(banner);
    return () => observer.disconnect();
  }, [poll.items.length, poll.visible, expanded]);

  if (!poll.visible) return null;

  const shouldCollapse = poll.items.length > 3;
  const visibleItems =
    shouldCollapse && !expanded ? poll.items.slice(0, 3) : poll.items;

  return (
    <>
      <div
        className="article-progress-banner__slot"
        style={{ height: bannerHeight }}
        aria-hidden
      />
      <div
        ref={bannerRef}
        className="article-progress-banner"
        role="status"
        aria-live="polite"
      >
        <div className="article-progress-banner__inner">
          <div className="article-progress-banner__items">
            {visibleItems.map((item) => (
              <div
                className="article-progress-banner__item"
                key={item.articleId}
              >
                <div className="article-progress-banner__meta">
                  <span className="article-progress-banner__title">
                    《{item.title}》{articleStatusLabel(item.status)}
                  </span>
                  <span className="article-progress-banner__stage">
                    {item.stage}
                  </span>
                </div>
                {item.showUploadPercent ? (
                  <Progress
                    className="article-progress-banner__bar"
                    percent={item.uploadPercent}
                    showInfo
                    status="active"
                    strokeColor="var(--color-primary)"
                    size="small"
                  />
                ) : item.showUploadComplete ? (
                  <Progress
                    className="article-progress-banner__bar"
                    percent={100}
                    showInfo={false}
                    status="success"
                    size="small"
                  />
                ) : item.showAuditWaiting ? (
                  <div
                    className="article-progress-banner__bar article-progress-banner__bar--audit"
                    aria-hidden
                  />
                ) : null}
              </div>
            ))}
          </div>
          {shouldCollapse ? (
            <button
              className="article-progress-banner__toggle"
              type="button"
              aria-expanded={expanded}
              onClick={() => setExpanded((value) => !value)}
            >
              {expanded ? '收起' : `展开全部（${poll.items.length}）`}
            </button>
          ) : null}
        </div>
      </div>
    </>
  );
};

export default memo(ArticleProgressBanner);
