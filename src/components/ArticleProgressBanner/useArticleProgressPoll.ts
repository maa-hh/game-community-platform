import { useEffect, useRef } from 'react';
import { message } from 'antd';

import {
  ARTICLE_STATUS,
  assertArticlePublicId,
  getMyArticleDetailApi,
  getArticleProgressApi,
  TASK_STATUS,
  type IArticleProgress,
} from '@/service/content';
import {
  clearArticleProgressTrack,
  updateArticleProgress,
  type ArticleProgressTask,
} from '@/store/modules/articleProgress';
import { useAppDispatch, useAppSelector } from '@/store';
import { invalidateOwnProfilePostCache } from '@/utils/profileDataCache';
import {
  resolveArticleProgressBanner,
  resolveArticleProgressStop,
  shouldPollArticleProgress,
} from '@/utils/articleProgressMessage';

import {
  ARTICLE_PROGRESS_MAX_QUERY_FAILURES,
  ARTICLE_PROGRESS_POLL_MS,
} from './config';

interface ArticleProgressPollMeta {
  consecutiveFailures: number;
  pendingPolls: number;
  lastArticleStatusCheckAt: number;
}

export function useArticleProgressPoll() {
  const dispatch = useAppDispatch();
  const tasks = useAppSelector((state) => state.articleProgress.tasks);
  const accountId = useAppSelector((state) => state.auth.user?.accountId);
  const tasksRef = useRef(tasks);

  useEffect(() => {
    tasksRef.current = tasks;
  }, [tasks]);

  // 只有待审核中的任务需要轮询；新增或结束一个任务时重新建立轮询集合。
  const pollKey = tasks
    .filter((task) => {
      const status = task.progress?.status ?? ARTICLE_STATUS.PENDING;
      return task.kind === 'audit' && shouldPollArticleProgress(status);
    })
    .map((task) => task.articleId)
    .join('|');

  useEffect(() => {
    if (!pollKey) return undefined;

    let stopped = false;
    const inFlight = new Set<string>();
    const pollMeta = new Map<string, ArticleProgressPollMeta>();

    const getMeta = (articleId: string): ArticleProgressPollMeta => {
      const existing = pollMeta.get(articleId);
      if (existing) return existing;

      const next: ArticleProgressPollMeta = {
        consecutiveFailures: 0,
        pendingPolls: 0,
        lastArticleStatusCheckAt: 0,
      };
      pollMeta.set(articleId, next);
      return next;
    };

    const updateProgressState = (
      task: ArticleProgressTask,
      progress: IArticleProgress,
    ) => {
      const previousStatus = task.progress?.status;
      dispatch(updateArticleProgress(progress));
      if (previousStatus !== progress.status) {
        invalidateOwnProfilePostCache(
          accountId,
          progress.status === ARTICLE_STATUS.PUBLISHED ? 'published' : 'draft',
        );
      }
    };

    const finish = (
      articleId: string,
      payload: { type: 'success' | 'error' | 'info'; text: string },
    ) => {
      // 只移除当前作品，其他作品的审核任务继续保留并轮询。
      dispatch(clearArticleProgressTrack(articleId));
      message.open({ type: payload.type, content: payload.text, duration: 3 });
    };

    const pollTask = async (task: ArticleProgressTask) => {
      const articleId = task.articleId;
      const meta = getMeta(articleId);
      if (stopped || inFlight.has(articleId)) return;
      inFlight.add(articleId);

      try {
        const res = await getArticleProgressApi(articleId);
        if (stopped) return;

        meta.consecutiveFailures = 0;
        const progress = res.data;
        const expectedArticleId = assertArticlePublicId(articleId);
        const actualArticleId = assertArticlePublicId(progress.articleId);
        if (actualArticleId !== expectedArticleId) {
          finish(articleId, {
            type: 'error',
            text: `《${task.title || '内容'}》进度 ID 不一致，请刷新页面后重试`,
          });
          return;
        }

        if (progress.status === ARTICLE_STATUS.PENDING) {
          meta.pendingPolls += 1;
        } else {
          meta.pendingPolls = 0;
        }

        // 文章主状态是发布结果的权威字段；若进度接口短暂仍返回 pending，
        // 用作者详情做一次兜底校验，避免状态栏一直停留在“排队审核中”。
        const now = Date.now();
        const shouldReconcileArticleStatus =
          progress.status === ARTICLE_STATUS.PENDING &&
          (meta.pendingPolls === 1 ||
            progress.taskStatus === TASK_STATUS.COMPLETED ||
            (meta.pendingPolls >= 5 &&
              now - meta.lastArticleStatusCheckAt >= 10_000));

        if (shouldReconcileArticleStatus) {
          meta.lastArticleStatusCheckAt = now;
          try {
            const detailRes = await getMyArticleDetailApi(articleId);
            if (stopped) return;
            const articleStatus = Number(detailRes.data.status);
            if (
              Number.isFinite(articleStatus) &&
              articleStatus !== ARTICLE_STATUS.PENDING
            ) {
              const reconciledProgress = {
                ...progress,
                status: articleStatus,
                auditMessage:
                  detailRes.data.auditMessage || progress.auditMessage,
                auditStageText:
                  articleStatus === ARTICLE_STATUS.PUBLISHED
                    ? '已发布'
                    : articleStatus === ARTICLE_STATUS.REJECTED
                      ? '已驳回'
                      : articleStatus === ARTICLE_STATUS.OFFLINE
                        ? '已取消上架'
                        : articleStatus === ARTICLE_STATUS.DRAFT
                          ? '草稿'
                          : progress.auditStageText,
              };
              updateProgressState(task, reconciledProgress);
              const stop = resolveArticleProgressStop(
                reconciledProgress,
                task.title || '内容',
              );
              if (stop) finish(articleId, stop.message);
              return;
            }
          } catch {
            // 作者详情不可读时仍以进度接口为准，继续正常轮询。
          }
        }

        updateProgressState(task, progress);
        const stop = resolveArticleProgressStop(progress, task.title || '内容');
        if (stop) finish(articleId, stop.message);
      } catch {
        if (stopped) return;
        meta.consecutiveFailures += 1;
        if (meta.consecutiveFailures >= ARTICLE_PROGRESS_MAX_QUERY_FAILURES) {
          finish(articleId, {
            type: 'error',
            text: `《${task.title || '内容'}》进度暂时无法获取，请在个人页查看状态`,
          });
        }
      } finally {
        inFlight.delete(articleId);
      }
    };

    const tick = () => {
      if (stopped) return;
      tasksRef.current
        .filter((task) => {
          const status = task.progress?.status ?? ARTICLE_STATUS.PENDING;
          return task.kind === 'audit' && shouldPollArticleProgress(status);
        })
        .forEach((task) => {
          void pollTask(task);
        });
    };

    tick();
    const timer = window.setInterval(tick, ARTICLE_PROGRESS_POLL_MS);
    return () => {
      stopped = true;
      window.clearInterval(timer);
    };
  }, [accountId, dispatch, pollKey]);

  const items = tasks
    .filter(
      (task) =>
        task.kind === 'audit' ||
        task.kind === 'draft' ||
        task.kind === 'upload',
    )
    .map((task) => {
      const progress = task.progress;
      const status = progress?.status ?? ARTICLE_STATUS.PENDING;
      if (task.kind === 'audit' && !shouldPollArticleProgress(status)) {
        return null;
      }

      const banner = resolveArticleProgressBanner(progress);
      return {
        articleId: task.articleId,
        title: task.title,
        status,
        stage: banner.stage,
        uploadPercent: banner.uploadPercent,
        showUploadPercent: banner.showUploadPercent,
        showUploadComplete: banner.showUploadComplete,
        showPreparation: banner.showPreparation,
        showAuditWaiting: banner.showAuditWaiting,
      };
    })
    .filter((item): item is NonNullable<typeof item> => item !== null);

  return {
    visible: items.length > 0,
    items,
  };
}
