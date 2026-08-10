import { ARTICLE_STATUS, articleStatusLabel } from '@/service/content';
import type { PostSubTabKey } from '@/types/profile';

/** 个人页子 Tab：非已发布均归入草稿（含主动保存、下架、审核中、驳回） */
export function mapArticleStatusToTab(status: number): PostSubTabKey {
  if (status === ARTICLE_STATUS.PUBLISHED) return 'published';
  return 'draft';
}

/** 个人页列表状态文案 */
export function profileArticleStatusLabel(status: number): string {
  if (status === ARTICLE_STATUS.OFFLINE) return '已下架';
  return articleStatusLabel(status);
}
