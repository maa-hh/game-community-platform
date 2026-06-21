import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { auditApi, AuditReport, AuditReportDetail } from "../api/audit";
import { ActionButton } from "../components/ActionButton";
import { Notice } from "../components/Notice";
import { friendlyError } from "../utils/errors";

const targetLabels: Record<number, string> = {
  1: "文章",
  2: "评论",
  3: "回复",
  4: "用户"
};

const statusLabels: Record<number, string> = {
  0: "待处理",
  1: "已通过",
  2: "已驳回",
  3: "处理中"
};

export function AuditReportPage() {
  const navigate = useNavigate();
  const [reports, setReports] = useState<AuditReport[]>([]);
  const [selected, setSelected] = useState<AuditReportDetail | null>(null);
  const [status, setStatus] = useState<number | undefined>(0);
  const [remark, setRemark] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    void loadReports();
  }, [status]);

  async function loadReports() {
    setError(null);
    try {
      const result = await auditApi.listReports({ status, size: 30 });
      setReports(result.data ?? []);
      if (selected) {
        const next = (result.data ?? []).find((item) => item.id === selected.id);
        if (!next) {
          setSelected(null);
        }
      }
    } catch (err) {
      setError(friendlyError(err));
    }
  }

  async function openDetail(taskId: number) {
    setError(null);
    setMessage(null);
    try {
      setSelected(await auditApi.getReport(taskId));
      setRemark("");
    } catch (err) {
      setError(friendlyError(err));
    }
  }

  async function handleReport(nextStatus: number) {
    if (!selected) {
      return;
    }
    setError(null);
    setMessage(null);
    try {
      await auditApi.handleReport(selected.id, { status: nextStatus, handleRemark: remark.trim() || undefined });
      setMessage(nextStatus === 1 ? "已确认违规并执行处理。" : "已驳回该举报。");
      setSelected(await auditApi.getReport(selected.id));
      await loadReports();
    } catch (err) {
      setError(friendlyError(err));
    }
  }

  return (
    <section className="page-view audit-page">
      <div className="page-heading compact-heading">
        <p className="eyebrow">审核中心</p>
        <h1>举报人工审核</h1>
        <span>用户举报会先进入社交服务，再通过 Kafka 生成这里的审核工单；点开详情时才通过 Feign 拉取目标内容。</span>
      </div>

      <div className="content-detail-actions">
        <ActionButton variant={status === 0 ? "soft" : "ghost"} onClick={() => setStatus(0)}>待处理</ActionButton>
        <ActionButton variant={status === undefined ? "soft" : "ghost"} onClick={() => setStatus(undefined)}>全部</ActionButton>
        <ActionButton variant={status === 1 ? "soft" : "ghost"} onClick={() => setStatus(1)}>已通过</ActionButton>
        <ActionButton variant={status === 2 ? "soft" : "ghost"} onClick={() => setStatus(2)}>已驳回</ActionButton>
      </div>

      {error && <Notice type="error">{error}</Notice>}
      {message && <Notice>{message}</Notice>}

      <div className="audit-layout">
        <div className="audit-list">
          {reports.length === 0 && <Notice>当前没有符合条件的举报工单。</Notice>}
          {reports.map((report) => (
            <button
              key={report.id}
              type="button"
              className={`audit-row ${selected?.id === report.id ? "active" : ""}`}
              onClick={() => void openDetail(report.id)}
            >
              <strong>{targetLabels[report.targetType] ?? "未知"} #{report.targetId}</strong>
              <span>{statusLabels[report.status] ?? "未知状态"} · 举报人 {report.reporterName ?? report.reporterId}</span>
              <em>{report.reason}</em>
            </button>
          ))}
        </div>

        <article className="audit-detail-card">
          {!selected && <Notice>选择左侧举报查看详情。</Notice>}
          {selected && (
            <>
              <span className="audit-pill">{targetLabels[selected.targetType] ?? "未知"}举报 · {statusLabels[selected.status]}</span>
              <h2>{selected.targetTitle ?? `目标 #${selected.targetId}`}</h2>
              <p className="muted">举报原因：{selected.reason}</p>
              <p className="muted">举报人：{selected.reporterName ?? selected.reporterId}；被举报人：{selected.reportedUserName ?? selected.reportedUserId ?? "未知"}</p>
              <AuditTargetDetail selected={selected} onNavigate={navigate} />
              <label>
                处理说明
                <textarea value={remark} onChange={(event) => setRemark(event.target.value)} placeholder="可填写违规原因或驳回说明" />
              </label>
              {selected.status === 0 && (
                <div className="content-detail-actions">
                  <ActionButton onClick={() => void handleReport(1)}>确认违规并处理</ActionButton>
                  <ActionButton variant="ghost" onClick={() => void handleReport(2)}>驳回举报</ActionButton>
                </div>
              )}
            </>
          )}
        </article>
      </div>
    </section>
  );
}

function AuditTargetDetail({
  selected,
  onNavigate
}: {
  selected: AuditReportDetail;
  onNavigate: (path: string) => void;
}) {
  const target = selected.target as Record<string, unknown> | null | undefined;
  const articleId = selected.targetType === 1
    ? selected.targetId
    : typeof target?.articleId === "number"
      ? target.articleId
      : undefined;
  const targetUserId = typeof selected.reportedUserId === "number"
    ? selected.reportedUserId
    : typeof target?.userId === "number"
      ? target.userId
      : undefined;
  const accountId = typeof target?.accountId === "number" ? target.accountId : undefined;
  const summary = typeof target?.summary === "string" ? target.summary : undefined;
  const content = selected.targetContent || (typeof target?.content === "string" ? target.content : undefined);
  const username = typeof target?.username === "string" ? target.username : selected.reportedUserName;
  const signature = typeof target?.signature === "string" ? target.signature : undefined;
  const status = typeof target?.status === "number" ? target.status : undefined;

  return (
    <div className="audit-target-box">
      <div className="audit-target-actions">
        {articleId && (
          <ActionButton variant="ghost" onClick={() => onNavigate(`/app/modules/content/${articleId}`)}>
            查看文章
          </ActionButton>
        )}
        {targetUserId && (
          <ActionButton variant="ghost" onClick={() => onNavigate(`/app/users/id/${targetUserId}`)}>
            查看用户主页
          </ActionButton>
        )}
      </div>

      {selected.targetType === 4 && (
        <div className="audit-target-grid">
          <article>
            <span>用户</span>
            <strong>{username ?? `用户 #${selected.targetId}`}</strong>
          </article>
          <article>
            <span>账号 ID</span>
            <strong>{accountId ? `#${accountId}` : "未获取"}</strong>
          </article>
          <article>
            <span>状态</span>
            <strong>{status === 1 ? "已禁用" : "正常"}</strong>
          </article>
        </div>
      )}

      {summary && (
        <section>
          <strong>摘要</strong>
          <p>{summary}</p>
        </section>
      )}
      {signature && (
        <section>
          <strong>个性签名</strong>
          <p>{signature}</p>
        </section>
      )}
      <section>
        <strong>{selected.targetType === 2 ? "评论内容" : selected.targetType === 3 ? "回复内容" : "正文/详情"}</strong>
        <p>{content || "暂无可展示内容，可能目标已被删除或服务暂不可用。"}</p>
      </section>
    </div>
  );
}
