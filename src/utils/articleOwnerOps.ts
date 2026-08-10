import { ARTICLE_STATUS, POST_TYPE } from '@/service/content';

export interface ArticleOwnerOpsConfig {
  status?: number;
  postType?: number;
}

export function canEditArticle(postType?: number): boolean {
  return postType != null && postType !== POST_TYPE.REPOST;
}

/** 已发布 → 下架；草稿/驳回/下架 → 上架；审核中 → 取消上架 */
export function getPublishToggleLabel(status?: number): string | null {
  if (status === ARTICLE_STATUS.PUBLISHED) return '下架';
  if (status === ARTICLE_STATUS.PENDING) return '取消上架';
  if (
    status === ARTICLE_STATUS.DRAFT ||
    status === ARTICLE_STATUS.OFFLINE ||
    status === ARTICLE_STATUS.REJECTED
  ) {
    return '上架';
  }
  return null;
}

export function canShowPublishToggle(status?: number): boolean {
  return getPublishToggleLabel(status) != null;
}

export function isPublishAction(status?: number): boolean {
  return (
    status === ARTICLE_STATUS.DRAFT ||
    status === ARTICLE_STATUS.OFFLINE ||
    status === ARTICLE_STATUS.REJECTED
  );
}

export function getOwnerOpsConfig(
  status?: number,
  postType?: number,
): ArticleOwnerOpsConfig & {
  canEdit: boolean;
  toggleLabel: string | null;
  isPublish: boolean;
} {
  return {
    status,
    postType,
    canEdit: canEditArticle(postType),
    toggleLabel: getPublishToggleLabel(status),
    isPublish: isPublishAction(status),
  };
}
