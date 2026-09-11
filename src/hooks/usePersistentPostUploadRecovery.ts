import { useEffect } from 'react';
import { App as AntdApp } from 'antd';

import { ARTICLE_STATUS, TASK_STATUS } from '@/service/content';
import {
  clearArticleProgressTrack,
  startArticleProgressTrack,
  updateArticleProgress,
} from '@/store/modules/articleProgress';
import { useAppDispatch, useAppSelector } from '@/store';
import {
  subscribePersistentPostUploads,
  type PersistentPostUploadTask,
} from '@/utils/persistentPostUpload';
import { formatApiError } from '@/utils/apiError';
import { invalidateOwnProfilePostCaches } from '@/utils/profileDataCache';

function getTaskTitle(task: PersistentPostUploadTask): string {
  return task.payload.title?.trim() || '内容';
}

export function usePersistentPostUploadRecovery(): void {
  const dispatch = useAppDispatch();
  const { message } = AntdApp.useApp();
  const accountId = useAppSelector((state) => state.auth.user?.accountId);

  useEffect(() => {
    if (accountId == null) return undefined;

    const handleTask = (task: PersistentPostUploadTask) => {
      if (task.accountId !== accountId) return;
      const title = getTaskTitle(task);
      const isDraft = task.payload.status === ARTICLE_STATUS.DRAFT;
      const trackedArticleId = task.articleId || task.id;

      if (task.status === 'QUEUED' && task.articleId && !isDraft) {
        dispatch(
          startArticleProgressTrack({
            articleId: task.articleId,
            title,
            kind: 'audit',
            progress: {
              articleId: task.articleId,
              status: ARTICLE_STATUS.PENDING,
              uploadPercent: 100,
              uploadStatus: 'MERGED',
              auditStageText: '排队审核中',
            },
          }),
        );
        return;
      }

      if (
        task.status === 'QUEUED' ||
        task.status === 'PREPARING' ||
        task.status === 'UPLOADING'
      ) {
        const preparing = task.status !== 'UPLOADING';
        dispatch(
          startArticleProgressTrack({
            articleId: task.id,
            title,
            kind: 'upload',
            progress: {
              articleId: task.id,
              status: isDraft ? ARTICLE_STATUS.DRAFT : ARTICLE_STATUS.PENDING,
              uploadPercent: task.progress,
              uploadStatus: preparing ? 'PREPARING' : 'UPLOADING',
              auditStageText: preparing ? '准备媒体上传' : '媒体上传中',
            },
          }),
        );
        return;
      }

      if (task.status === 'SAVING') {
        dispatch(
          updateArticleProgress({
            articleId: trackedArticleId,
            status: isDraft ? ARTICLE_STATUS.DRAFT : ARTICLE_STATUS.PENDING,
            uploadPercent: 100,
            uploadStatus: 'MERGED',
            auditStageText: isDraft ? '保存草稿中' : '提交审核中',
          }),
        );
        return;
      }

      if (task.status === 'FAILED') {
        dispatch(
          updateArticleProgress({
            articleId: trackedArticleId,
            status: isDraft ? ARTICLE_STATUS.DRAFT : ARTICLE_STATUS.PENDING,
            uploadPercent: task.progress,
            uploadStatus: 'FAILED',
            taskStatus: TASK_STATUS.FAILED,
            taskErrorMessage: formatApiError(
              '上传或保存失败',
              new Error(task.errorMessage || '请稍后重试'),
            ),
            auditStageText: '上传失败',
          }),
        );
        return;
      }

      if (task.status === 'CANCELLED') {
        dispatch(clearArticleProgressTrack(trackedArticleId));
        return;
      }

      dispatch(clearArticleProgressTrack(task.id));
      if (!task.articleId) return;

      invalidateOwnProfilePostCaches(accountId, ['draft']);
      if (isDraft) {
        dispatch(
          startArticleProgressTrack({
            articleId: task.articleId,
            title,
            kind: 'draft',
            progress: {
              articleId: task.articleId,
              status: ARTICLE_STATUS.DRAFT,
              uploadPercent: 100,
              uploadStatus: 'SAVED',
              auditStageText: '草稿已保存',
            },
          }),
        );
        window.setTimeout(
          () => dispatch(clearArticleProgressTrack(task.articleId)),
          4000,
        );
      } else {
        dispatch(
          startArticleProgressTrack({
            articleId: task.articleId,
            title,
            kind: 'audit',
            progress: {
              articleId: task.articleId,
              status: ARTICLE_STATUS.PENDING,
              uploadPercent: 100,
              uploadStatus: 'MERGED',
              auditStageText: '排队审核中',
            },
          }),
        );
        message.success(`《${title}》已提交审核`);
      }
    };

    const unsubscribe = subscribePersistentPostUploads(handleTask);
    return unsubscribe;
  }, [accountId, dispatch, message]);
}
