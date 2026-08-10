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

import { ARTICLE_PROGRESS_POLL_MS } from './config';

export function useArticleProgressPoll() {
  const dispatch = useAppDispatch();
  const task = useAppSelector((state) => state.articleProgress.task);

  useEffect(() => {
    if (!task || task.kind !== 'audit') return undefined;
    const status = task.progress?.status ?? ARTICLE_STATUS.PENDING;
    if (!shouldPollArticleProgress(status)) return undefined;

    let stopped = false;

    const finish = (payload: {
      type: 'success' | 'error' | 'info';
      text: string;
    }) => {
      dispatch(clearArticleProgressTrack());
      message.open({ type: payload.type, content: payload.text, duration: 3 });
    };

    const tick = async () => {
      try {
        const res = await getArticleProgressApi(task.articleId);
        if (stopped) return;
        dispatch(updateArticleProgress(res.data));
        const stop = resolveArticleProgressStop(res.data, task.title);
        if (stop) {
          finish(stop.message);
        }
      } catch {
        if (stopped) return;
        dispatch(clearArticleProgressTrack());
        message.error({
          content: `《${task.title}》进度查询失败，请在个人页查看状态`,
          duration: 1,
        });
      }
    };

    void tick();
    const timer = window.setInterval(tick, ARTICLE_PROGRESS_POLL_MS);
    return () => {
      stopped = true;
      window.clearInterval(timer);
    };
  }, [dispatch, task]);

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
