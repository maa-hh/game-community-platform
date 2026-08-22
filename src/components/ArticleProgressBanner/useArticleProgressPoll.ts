import { useEffect } from 'react';
import { message } from 'antd';

import { ARTICLE_STATUS, getArticleProgressApi } from '@/service/content';
import {
  clearArticleProgressTrack,
  updateArticleProgress,
} from '@/store/modules/articleProgress';
import { useAppDispatch, useAppSelector } from '@/store';
import {
  resolveArticleProgressBanner,
  resolveArticleProgressStop,
  shouldPollArticleProgress,
} from '@/utils/articleProgressMessage';

import {
  ARTICLE_PROGRESS_MAX_QUERY_FAILURES,
  ARTICLE_PROGRESS_POLL_MS,
} from './config';

export function useArticleProgressPoll() {
  const dispatch = useAppDispatch();
  const task = useAppSelector((state) => state.articleProgress.task);
  const articleId = task?.articleId;
  const taskKind = task?.kind;
  const title = task?.title;
  const currentStatus = task?.progress?.status ?? ARTICLE_STATUS.PENDING;

  useEffect(() => {
    if (!articleId || taskKind !== 'audit') return undefined;
    if (!shouldPollArticleProgress(currentStatus)) return undefined;

    let stopped = false;
    let inFlight = false;
    let consecutiveFailures = 0;

    const finish = (payload: {
      type: 'success' | 'error' | 'info';
      text: string;
    }) => {
      dispatch(clearArticleProgressTrack());
      message.open({ type: payload.type, content: payload.text, duration: 3 });
    };

    const tick = async () => {
      if (stopped || inFlight) return;
      inFlight = true;

      try {
        const res = await getArticleProgressApi(articleId);
        if (stopped) return;

        consecutiveFailures = 0;
        dispatch(updateArticleProgress(res.data));
        const stop = resolveArticleProgressStop(res.data, title || '内容');
        if (stop) {
          finish(stop.message);
        }
      } catch {
        if (stopped) return;
        consecutiveFailures += 1;
        if (consecutiveFailures >= ARTICLE_PROGRESS_MAX_QUERY_FAILURES) {
          dispatch(clearArticleProgressTrack());
          message.error({
            content: `《${title || '内容'}》进度暂时无法获取，请在个人页查看状态`,
            duration: 2,
          });
        }
      } finally {
        inFlight = false;
      }
    };

    void tick();
    const timer = window.setInterval(tick, ARTICLE_PROGRESS_POLL_MS);
    return () => {
      stopped = true;
      window.clearInterval(timer);
    };
  }, [articleId, currentStatus, dispatch, taskKind, title]);

  if (!task || task.kind !== 'audit') {
    return { visible: false as const };
  }

  const progress = task.progress;
  const status = progress?.status ?? ARTICLE_STATUS.PENDING;
  if (!shouldPollArticleProgress(status)) {
    return { visible: false as const };
  }

  const banner = resolveArticleProgressBanner(progress);

  return {
    visible: true as const,
    title: task.title,
    status,
    stage: banner.stage,
    uploadPercent: banner.uploadPercent,
    showUploadPercent: banner.showUploadPercent,
    showAuditWaiting: banner.showAuditWaiting,
  };
}
