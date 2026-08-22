import hyRequest from '@/service/request';
import type { IPageResult } from '@/service/types';

export type ModerationTaskType = 'REPORT' | 'ARTICLE_AUDIT' | 'PROFILE_AUDIT';

export type ModerationTaskStatus = 0 | 1 | 2;

export type ModerationHandleAction =
  | 'NO_VIOLATION'
  | 'OFFLINE_ARTICLE'
  | 'HIDE_COMMENT'
  | 'HIDE_REPLY'
  | 'HIDE_DANMAKU'
  | 'BAN_USER'
  | 'AUDIT_APPROVE'
  | 'AUDIT_REJECT'
  | 'PROFILE_APPROVE'
  | 'PROFILE_REJECT';

export interface IModerationTask {
  taskKey: string;
  taskType: ModerationTaskType;
  targetType?: number;
  targetPublicId?: string;
  targetAccountId?: number;
  subjectAccountId?: number;
  reporterAccountId?: number;
  subjectUserName?: string;
  reporterName?: string;
  reason?: string;
  summary?: string;
  extraPayload?: string;
  status: ModerationTaskStatus;
  handleAction?: string;
  handlerAccountId?: number;
  handlerName?: string;
  handleRemark?: string;
  claimTime?: string;
  handleTime?: string;
  createTime?: string;
}

export interface IModerationTaskDetail extends IModerationTask {
  claimToken?: string;
  leaseExpireTime?: string;
  targetTitle?: string;
  targetContent?: string;
  targetStatusSnapshot?: string;
  targetUpdatedAt?: string;
  currentTargetStatus?: string;
  currentTargetUpdatedAt?: string;
  targetContentChanged?: boolean;
  reviewable?: boolean;
  reviewBlockReason?: string;
  availableReportActions?: ModerationHandleAction[];
}

export function fetchModerationTasksApi(params: {
  page?: number;
  size?: number;
  status?: ModerationTaskStatus;
  taskType?: ModerationTaskType;
}) {
  return hyRequest.get<IPageResult<IModerationTask>>({
    url: '/audit/moderation/page',
    params,
  });
}

export function fetchModerationTaskDetailApi(taskKey: string) {
  return hyRequest.get<{
    code: number;
    data: IModerationTaskDetail;
    message: string;
  }>({
    url: `/audit/moderation/${taskKey}`,
  });
}

export function claimModerationTaskApi(taskKey: string) {
  return hyRequest.post<{
    code: number;
    data: {
      taskKey: string;
      claimToken: string;
      handlerAccountId?: number;
      version: number;
      leaseExpireTime: string;
    };
    message: string;
  }>({
    url: `/audit/moderation/${taskKey}/claim`,
  });
}

export function handleModerationTaskApi(
  taskKey: string,
  payload: {
    handleAction: ModerationHandleAction;
    handleRemark?: string;
    claimToken: string;
    requestId: string;
  },
) {
  return hyRequest.put<{ code: number; data: null; message: string }>({
    url: `/audit/moderation/${taskKey}`,
    data: payload,
  });
}
