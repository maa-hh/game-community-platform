import hyRequest from '@/service/request';
import type { IPageResult } from '@/service/types';

export type ModerationTaskType = 'REPORT' | 'ARTICLE_AUDIT' | 'PROFILE_AUDIT';

export type ModerationTaskStatus = 0 | 1 | 2;

export type ModerationHandleAction =
  | 'NO_VIOLATION'
  | 'OFFLINE_ARTICLE'
  | 'HIDE_COMMENT'
  | 'HIDE_REPLY'
  | 'BAN_USER'
  | 'AUDIT_APPROVE'
  | 'AUDIT_REJECT'
  | 'PROFILE_APPROVE'
  | 'PROFILE_REJECT';

export interface IModerationTask {
  id: number;
  taskType: ModerationTaskType;
  sourceId?: number;
  targetType?: number;
  targetId?: number;
  targetPublicId?: string;
  subjectUserId?: number;
  reporterId?: number;
  subjectUserName?: string;
  reporterName?: string;
  reason?: string;
  summary?: string;
  extraPayload?: string;
  status: ModerationTaskStatus;
  handleAction?: string;
  handlerId?: number;
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
  target?: unknown;
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

export function fetchModerationTaskDetailApi(taskId: number) {
  return hyRequest.get<{
    code: number;
    data: IModerationTaskDetail;
    message: string;
  }>({
    url: `/audit/moderation/${taskId}`,
  });
}

export function claimModerationTaskApi(taskId: number) {
  return hyRequest.post<{
    code: number;
    data: {
      taskId: number;
      claimToken: string;
      handlerId: number;
      version: number;
      leaseExpireTime: string;
    };
    message: string;
  }>({
    url: `/audit/moderation/${taskId}/claim`,
  });
}

export function handleModerationTaskApi(
  taskId: number,
  payload: {
    handleAction: ModerationHandleAction;
    handleRemark?: string;
    claimToken: string;
    requestId: string;
  },
) {
  return hyRequest.put<{ code: number; data: null; message: string }>({
    url: `/audit/moderation/${taskId}`,
    data: payload,
  });
}
