import {
  ARTICLE_STATUS,
  TASK_STATUS,
  type IArticleProgress,
} from '@/service/content';

export type ArticleProgressKind = 'audit' | 'draft' | 'unpublish';

export type ArticleProgressStopReason =
  'terminal' | 'human_review' | 'task_failed';

export interface ArticleProgressStop {
  reason: ArticleProgressStopReason;
  message: { type: 'success' | 'error' | 'info'; text: string };
}

export interface ArticleProgressBannerView {
  stage: string;
  showUploadPercent: boolean;
  uploadPercent?: number;
  showAuditWaiting: boolean;
}

function progressTitle(title: string): string {
  return title?.trim() || '内容';
}

function includesHumanReviewHint(text?: string | null): boolean {
  if (!text) return false;
  return /人工|待审|复核/.test(text);
}

/** 仅在上传分片中时展示上传百分比（封面/视频已就绪后的 100% 不代表审核完成） */
export function isArticleUploading(
  progress: IArticleProgress | null | undefined,
) {
  return progress?.uploadStatus === 'UPLOADING';
}

export function resolveArticleProgressStage(
  progress: IArticleProgress | null | undefined,
): string {
  if (!progress) return '审核处理中';
  if (isArticleUploading(progress)) {
    return '媒体上传中';
  }
  if (progress.taskStatus === TASK_STATUS.FAILED) {
    return progress.taskErrorMessage || progress.auditMessage || '发布处理失败';
  }
  if (progress.auditStageText) {
    return progress.auditStageText;
  }
  if (progress.auditMessage) {
    return progress.auditMessage;
  }
  if (progress.taskStatus === TASK_STATUS.RUNNING) {
    return '审核执行中';
  }
  if (progress.taskStatus === TASK_STATUS.PENDING) {
    return '排队审核中';
  }
  return '审核处理中';
}

export function resolveArticleProgressBanner(
  progress: IArticleProgress | null | undefined,
): ArticleProgressBannerView {
  const uploading = isArticleUploading(progress);
  const uploadPercent = progress?.uploadPercent ?? undefined;
  const showUploadPercent =
    uploading && uploadPercent != null && uploadPercent >= 0;

  return {
    stage: resolveArticleProgressStage(progress),
    showUploadPercent,
    uploadPercent: showUploadPercent ? uploadPercent : undefined,
    showAuditWaiting: !uploading,
  };
}

export function isArticleHumanReviewPending(
  progress: IArticleProgress | null | undefined,
): boolean {
  if (!progress || progress.status !== ARTICLE_STATUS.PENDING) return false;
  if (progress.taskStatus !== TASK_STATUS.COMPLETED) return false;
  return (
    includesHumanReviewHint(progress.auditMessage) ||
    includesHumanReviewHint(progress.auditStageText)
  );
}

export function isArticleTaskFailed(
  progress: IArticleProgress | null | undefined,
): boolean {
  return (
    progress?.status === ARTICLE_STATUS.PENDING &&
    progress.taskStatus === TASK_STATUS.FAILED
  );
}

export function resolveArticleProgressStop(
  progress: IArticleProgress,
  title: string,
): ArticleProgressStop | null {
  if (isArticleProgressTerminal(progress.status)) {
    return {
      reason: 'terminal',
      message: getArticleProgressResultMessage(progress.status, title),
    };
  }

  if (isArticleTaskFailed(progress)) {
    const name = progressTitle(title);
    const detail = progress.taskErrorMessage || progress.auditMessage;
    const suffix = detail ? `：${detail}` : '';
    return {
      reason: 'task_failed',
      message: {
        type: 'error',
        text: `《${name}》发布失败${suffix}，请在个人页「未发布」中修改后重新提交`,
      },
    };
  }

  if (isArticleHumanReviewPending(progress)) {
    const name = progressTitle(title);
    return {
      reason: 'human_review',
      message: {
        type: 'info',
        text: `《${name}》已进入人工审核，通过后将自动发布，请在个人页查看`,
      },
    };
  }

  return null;
}

/** 审核/草稿结束后提示文案（含去哪里查看） */
export function getArticleProgressResultMessage(
  status: number,
  title: string,
  kind: ArticleProgressKind = 'audit',
): { type: 'success' | 'error' | 'info'; text: string } {
  const name = title?.trim() || '内容';

  switch (status) {
    case ARTICLE_STATUS.PUBLISHED:
      return {
        type: 'success',
        text: `《${name}》已发布，可在首页或个人页「已发布」中查看`,
      };
    case ARTICLE_STATUS.REJECTED:
      return {
        type: 'error',
        text: `《${name}》审核未通过，请在个人页「未发布」中修改后重新提交`,
      };
    case ARTICLE_STATUS.DRAFT:
      return {
        type: 'success',
        text:
          kind === 'draft'
            ? `《${name}》草稿已保存，请在个人页「草稿」中查看`
            : `《${name}》已移入草稿，请在个人页「草稿」中查看`,
      };
    case ARTICLE_STATUS.OFFLINE:
      return {
        type: 'info',
        text: `《${name}》已取消上架，请在个人页「未发布」中查看`,
      };
    default:
      return {
        type: 'info',
        text: `《${name}》处理完成，请在个人页查看`,
      };
  }
}

export function isArticleProgressTerminal(status: number): boolean {
  return (
    status === ARTICLE_STATUS.PUBLISHED ||
    status === ARTICLE_STATUS.REJECTED ||
    status === ARTICLE_STATUS.OFFLINE
  );
}

export function shouldPollArticleProgress(status: number): boolean {
  return status === ARTICLE_STATUS.PENDING;
}
