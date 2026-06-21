import { request, requestEnvelope } from "./client";
import { PageEnvelope } from "./social";

export type AuditReport = {
  id: number;
  reportId: number;
  targetType: number;
  targetId: number;
  reporterId: number;
  reportedUserId?: number;
  reporterName?: string;
  reportedUserName?: string;
  reason: string;
  status: number;
  handlerId?: number;
  handleRemark?: string;
  handleTime?: string;
  createTime?: string;
};

export type AuditReportDetail = AuditReport & {
  targetTitle?: string;
  targetContent?: string;
  targetAuthorName?: string;
  target?: unknown;
};

export const auditApi = {
  listReports: (payload?: { page?: number; size?: number; status?: number; targetType?: number }) => {
    const params = new URLSearchParams({
      page: String(payload?.page ?? 1),
      size: String(payload?.size ?? 20)
    });
    if (payload?.status !== undefined) {
      params.set("status", String(payload.status));
    }
    if (payload?.targetType !== undefined) {
      params.set("targetType", String(payload.targetType));
    }
    return requestEnvelope<AuditReport[]>(`/audit/report/page?${params.toString()}`) as Promise<PageEnvelope<AuditReport>>;
  },
  getReport: (taskId: number) => request<AuditReportDetail>(`/audit/report/${taskId}`),
  handleReport: (taskId: number, payload: { status: number; handleRemark?: string }) =>
    request<void>(`/audit/report/${taskId}`, { method: "PUT", body: payload })
};
