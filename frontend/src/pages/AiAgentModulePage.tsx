import { FormEvent, useEffect, useMemo, useState } from "react";
import {
  AiChatMessage,
  AiChatResponse,
  AiKnowledgeDebug,
  AiKnowledgeDetail,
  AiKnowledgeDocument,
  AiKnowledgeStats,
  aiAgentApi
} from "../api/aiAgent";
import { ActionButton } from "../components/ActionButton";
import { Notice } from "../components/Notice";
import { useAuth } from "../features/auth/AuthContext";
import { friendlyError } from "../utils/errors";

type MainTab = "chat" | "knowledge" | "debug";

const SESSION_KEY = "game-community-ai-session-id";

function getSessionId() {
  const existing = localStorage.getItem(SESSION_KEY);
  if (existing) {
    return existing;
  }
  const next = typeof crypto !== "undefined" && typeof crypto.randomUUID === "function"
    ? crypto.randomUUID()
    : `ai-${Date.now()}-${Math.random().toString(16).slice(2)}`;
  localStorage.setItem(SESSION_KEY, next);
  return next;
}

function formatDate(value?: string) {
  if (!value) {
    return "刚刚";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat("zh-CN", {
    month: "numeric",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit"
  }).format(date);
}

function sourceTypeText(sourceType?: number) {
  if (sourceType === 1) {
    return "文本录入";
  }
  if (sourceType === 2) {
    return "文件上传";
  }
  return "未知来源";
}

function statusText(status?: number) {
  return status === 1 ? "启用中" : "已停用";
}

function indexStatusText(indexStatus?: number) {
  if (indexStatus === 1) {
    return "索引就绪";
  }
  if (indexStatus === 2) {
    return "索引中";
  }
  if (indexStatus === 3) {
    return "索引失败";
  }
  return "未索引";
}

function toPreview(text?: string) {
  if (!text) {
    return "暂无内容摘要。";
  }
  return text.length > 180 ? `${text.slice(0, 180)}...` : text;
}

export function AiAgentModulePage() {
  const { user } = useAuth();
  const isAdmin = user?.type === 1;
  const [tab, setTab] = useState<MainTab>("chat");
  const [notice, setNotice] = useState<{ type: "success" | "error" | "info"; text: string } | null>(null);
  const [loading, setLoading] = useState(true);
  const [sessionId] = useState(() => getSessionId());
  const [chatMessages, setChatMessages] = useState<AiChatMessage[]>([]);
  const [chatInput, setChatInput] = useState("");
  const [chatBusy, setChatBusy] = useState(false);
  const [lastChatResponse, setLastChatResponse] = useState<AiChatResponse | null>(null);
  const [knowledgeBusy, setKnowledgeBusy] = useState(false);
  const [knowledgeKeyword, setKnowledgeKeyword] = useState("");
  const [knowledgeDocs, setKnowledgeDocs] = useState<AiKnowledgeDocument[]>([]);
  const [knowledgeTotal, setKnowledgeTotal] = useState(0);
  const [selectedDetail, setSelectedDetail] = useState<AiKnowledgeDetail | null>(null);
  const [stats, setStats] = useState<AiKnowledgeStats | null>(null);
  const [textTitle, setTextTitle] = useState("");
  const [textSourceName, setTextSourceName] = useState("");
  const [textContent, setTextContent] = useState("");
  const [fileToUpload, setFileToUpload] = useState<File | null>(null);
  const [debugQuery, setDebugQuery] = useState("");
  const [debugTopK, setDebugTopK] = useState(6);
  const [debugBusy, setDebugBusy] = useState(false);
  const [debugResult, setDebugResult] = useState<AiKnowledgeDebug | null>(null);

  const emptyReferencesText = useMemo(() => {
    if (!lastChatResponse) {
      return "问一个问题后，命中的知识片段会展示在这里，方便你判断答案有没有引用到正确资料。";
    }
    if (lastChatResponse.references.length === 0) {
      return "这次回答没有带出命中的知识片段，可能是通用回复，也可能知识库里暂时还没有相关内容。";
    }
    return "";
  }, [lastChatResponse]);

  useEffect(() => {
    void loadPage();
  }, []);

  useEffect(() => {
    if (!isAdmin) {
      return;
    }
    if (tab === "knowledge") {
      void loadKnowledgeDocuments();
      void loadStats();
    }
    if (tab === "debug") {
      void loadStats();
    }
  }, [isAdmin, tab]);

  async function loadPage() {
    setLoading(true);
    try {
      const messages = await aiAgentApi.getSessionMessages(sessionId);
      setChatMessages(messages);
      if (isAdmin) {
        const [page, nextStats] = await Promise.all([
          aiAgentApi.pageDocuments({ page: 1, size: 12 }),
          aiAgentApi.getStats()
        ]);
        setKnowledgeDocs(page.data ?? []);
        setKnowledgeTotal(page.total ?? 0);
        setStats(nextStats);
      }
    } catch (error) {
      setNotice({ type: "error", text: friendlyError(error) });
    } finally {
      setLoading(false);
    }
  }

  async function loadKnowledgeDocuments() {
    try {
      const page = await aiAgentApi.pageDocuments({
        page: 1,
        size: 12,
        keyword: knowledgeKeyword.trim() || undefined
      });
      setKnowledgeDocs(page.data ?? []);
      setKnowledgeTotal(page.total ?? 0);
    } catch (error) {
      setNotice({ type: "error", text: friendlyError(error) });
    }
  }

  async function loadStats() {
    try {
      setStats(await aiAgentApi.getStats());
    } catch (error) {
      setNotice({ type: "error", text: friendlyError(error) });
    }
  }

  async function handleChatSubmit(event: FormEvent) {
    event.preventDefault();
    const message = chatInput.trim();
    if (!message) {
      setNotice({ type: "error", text: "先输入问题再发给 AI。" });
      return;
    }
    setChatBusy(true);
    setNotice(null);
    try {
      const optimisticMessage: AiChatMessage = {
        role: "user",
        content: message,
        time: new Date().toISOString()
      };
      setChatMessages((current) => [...current, optimisticMessage]);
      setChatInput("");
      const response = await aiAgentApi.chat(sessionId, message);
      setLastChatResponse(response);
      setChatMessages((current) => [
        ...current,
        {
          role: "assistant",
          content: response.answer,
          time: new Date().toISOString()
        }
      ]);
    } catch (error) {
      setChatMessages((current) => current.slice(0, -1));
      setNotice({ type: "error", text: friendlyError(error) });
    } finally {
      setChatBusy(false);
    }
  }

  async function handleClearSession() {
    setChatBusy(true);
    try {
      await aiAgentApi.clearSession(sessionId);
      setChatMessages([]);
      setLastChatResponse(null);
      setNotice({ type: "info", text: "当前会话记忆已清空，新的提问会从空上下文开始。" });
    } catch (error) {
      setNotice({ type: "error", text: friendlyError(error) });
    } finally {
      setChatBusy(false);
    }
  }

  async function handleAddText(event: FormEvent) {
    event.preventDefault();
    if (!textTitle.trim() || !textContent.trim()) {
      setNotice({ type: "error", text: "标题和知识内容都要填写。" });
      return;
    }
    setKnowledgeBusy(true);
    try {
      const savedDocument = await aiAgentApi.addKnowledgeText({
        title: textTitle.trim(),
        content: textContent.trim(),
        sourceName: textSourceName.trim() || undefined
      });
      setNotice({ type: "success", text: `知识文档《${savedDocument.title}》已写入并完成索引。` });
      setTextTitle("");
      setTextSourceName("");
      setTextContent("");
      await Promise.all([loadKnowledgeDocuments(), loadStats()]);
    } catch (error) {
      setNotice({ type: "error", text: friendlyError(error) });
    } finally {
      setKnowledgeBusy(false);
    }
  }

  async function handleUploadFile(event: FormEvent) {
    event.preventDefault();
    if (!fileToUpload) {
      setNotice({ type: "error", text: "先选一个 txt 或 md 文件。" });
      return;
    }
    setKnowledgeBusy(true);
    try {
      const savedDocument = await aiAgentApi.addKnowledgeFile(fileToUpload);
      setNotice({ type: "success", text: `文件《${savedDocument.title}》已上传并进入知识库。` });
      setFileToUpload(null);
      const fileInput = window.document.getElementById("ai-knowledge-file") as HTMLInputElement | null;
      if (fileInput) {
        fileInput.value = "";
      }
      await Promise.all([loadKnowledgeDocuments(), loadStats()]);
    } catch (error) {
      setNotice({ type: "error", text: friendlyError(error) });
    } finally {
      setKnowledgeBusy(false);
    }
  }

  async function openKnowledgeDetail(documentId: number) {
    try {
      setSelectedDetail(await aiAgentApi.getDocument(documentId));
    } catch (error) {
      setNotice({ type: "error", text: friendlyError(error) });
    }
  }

  async function handleDisableDocument(documentId: number) {
    setKnowledgeBusy(true);
    try {
      await aiAgentApi.disableDocument(documentId);
      setNotice({ type: "info", text: "文档已停用，不会继续参与检索。" });
      await Promise.all([loadKnowledgeDocuments(), loadStats()]);
      if (selectedDetail?.id === documentId) {
        await openKnowledgeDetail(documentId);
      }
    } catch (error) {
      setNotice({ type: "error", text: friendlyError(error) });
    } finally {
      setKnowledgeBusy(false);
    }
  }

  async function handleDeleteDocument(documentId: number) {
    setKnowledgeBusy(true);
    try {
      await aiAgentApi.deleteDocument(documentId);
      setNotice({ type: "info", text: "文档及其检索切片已删除。" });
      if (selectedDetail?.id === documentId) {
        setSelectedDetail(null);
      }
      await Promise.all([loadKnowledgeDocuments(), loadStats()]);
    } catch (error) {
      setNotice({ type: "error", text: friendlyError(error) });
    } finally {
      setKnowledgeBusy(false);
    }
  }

  async function handleReindexDocument(documentId: number) {
    setKnowledgeBusy(true);
    try {
      await aiAgentApi.reindexDocument(documentId);
      setNotice({ type: "success", text: "文档已经重新切片并重建索引。" });
      await Promise.all([loadKnowledgeDocuments(), loadStats()]);
      await openKnowledgeDetail(documentId);
    } catch (error) {
      setNotice({ type: "error", text: friendlyError(error) });
    } finally {
      setKnowledgeBusy(false);
    }
  }

  async function handleDebugSearch(event: FormEvent) {
    event.preventDefault();
    if (!debugQuery.trim()) {
      setNotice({ type: "error", text: "先输入一个调试问题。" });
      return;
    }
    setDebugBusy(true);
    try {
      setDebugResult(await aiAgentApi.debugSearch(debugQuery.trim(), debugTopK));
    } catch (error) {
      setNotice({ type: "error", text: friendlyError(error) });
    } finally {
      setDebugBusy(false);
    }
  }

  if (loading) {
    return (
      <section className="page-view">
        <div className="page-heading">
          <p className="eyebrow">AI Agent</p>
          <h1>正在接通 AI 助手...</h1>
          <span>会话记忆、知识库和混合检索会在这里统一呈现。</span>
        </div>
      </section>
    );
  }

  return (
    <section className="page-view">
      <div className="page-heading compact-heading">
        <p className="eyebrow">AI Agent</p>
        <h1>把知识库问答、调试和管理都收进一个页面。</h1>
        <span>聊天记忆写 Redis，只保留最近 10 轮；知识切片进 Elasticsearch，检索默认走 BM25 和语义双路混排。</span>
      </div>

      {notice && <Notice type={notice.type}>{notice.text}</Notice>}

      <div className="game-tab-row">
        <button type="button" className={tab === "chat" ? "active" : ""} onClick={() => setTab("chat")}>对话助手</button>
        {isAdmin && <button type="button" className={tab === "knowledge" ? "active" : ""} onClick={() => setTab("knowledge")}>知识库管理</button>}
        {isAdmin && <button type="button" className={tab === "debug" ? "active" : ""} onClick={() => setTab("debug")}>检索调试</button>}
      </div>

      {tab === "chat" && (
        <div className="ai-agent-layout">
          <div className="ai-chat-panel">
            <div className="ai-chat-toolbar">
              <div>
                <strong>当前会话</strong>
                <span>{sessionId}</span>
              </div>
              <ActionButton variant="ghost" busy={chatBusy} onClick={() => void handleClearSession()}>
                清空记忆
              </ActionButton>
            </div>

            {chatMessages.length === 0 && (
              <Notice type="info">现在还没有对话记录。你可以先问规则说明、系统能力或者知识库里已经录入的问题。</Notice>
            )}

            <div className="ai-chat-history">
              {chatMessages.map((message, index) => (
                <article key={`${message.time ?? "time"}-${index}`} className={`ai-chat-message ai-chat-message--${message.role}`}>
                  <header>
                    <strong>{message.role === "assistant" ? "AI 助手" : "你"}</strong>
                    <time>{formatDate(message.time)}</time>
                  </header>
                  <p>{message.content}</p>
                </article>
              ))}
            </div>

            <form className="ai-chat-composer" onSubmit={handleChatSubmit}>
              <textarea
                value={chatInput}
                onChange={(event) => setChatInput(event.target.value)}
                rows={5}
                placeholder="输入你想问 AI 的问题，比如“商城优惠券对发货链路有什么影响？”"
              />
              <div className="ai-inline-actions">
                <span>当前会话只保留最近 10 轮，适合做站内知识问答和产品说明。</span>
                <ActionButton type="submit" busy={chatBusy}>发送问题</ActionButton>
              </div>
            </form>
          </div>

          <aside className="ai-side-panel">
            <article className="game-detail-panel">
              <p className="eyebrow">引用片段</p>
              <h2>这次回答参考了什么</h2>
              {emptyReferencesText && <p>{emptyReferencesText}</p>}
              {(lastChatResponse?.references ?? []).length > 0 && (
                <div className="ai-reference-list">
                  {lastChatResponse?.references.map((reference) => (
                    <article key={reference.segmentId} className="ai-reference-card">
                      <strong>{reference.title}</strong>
                      <span>片段 #{reference.segmentOrder + 1} · 综合分 {reference.finalScore?.toFixed(3) ?? "0.000"}</span>
                      <p>{toPreview(reference.contentPreview)}</p>
                    </article>
                  ))}
                </div>
              )}
            </article>
          </aside>
        </div>
      )}

      {tab === "knowledge" && isAdmin && (
        <div className="ai-admin-layout">
          <div className="admin-grid">
            <article className="form-card ai-admin-card">
              <h2>文本知识录入</h2>
              <form className="catalog-search-row" onSubmit={handleAddText}>
                <input value={textTitle} onChange={(event) => setTextTitle(event.target.value)} placeholder="标题，例如：商城发货 FAQ" />
                <input value={textSourceName} onChange={(event) => setTextSourceName(event.target.value)} placeholder="来源名称，可选" />
                <textarea
                  value={textContent}
                  onChange={(event) => setTextContent(event.target.value)}
                  rows={10}
                  placeholder="把知识正文粘贴到这里，服务端会自动切片、生成向量并写入 ES。"
                />
                <ActionButton type="submit" busy={knowledgeBusy}>写入知识库</ActionButton>
              </form>
            </article>

            <article className="form-card ai-admin-card">
              <h2>文件上传</h2>
              <form className="catalog-search-row" onSubmit={handleUploadFile}>
                <input
                  id="ai-knowledge-file"
                  type="file"
                  accept=".txt,.md,text/plain,text/markdown"
                  onChange={(event) => setFileToUpload(event.target.files?.[0] ?? null)}
                />
                <span className="ai-muted">只建议上传 txt 或 md。上传后会自动生成切片并建立向量索引。</span>
                <ActionButton type="submit" busy={knowledgeBusy}>上传文件</ActionButton>
              </form>
            </article>
          </div>

          <div className="game-catalog-layout">
            <div className="admin-list">
              <div className="catalog-search-row">
                <div className="shop-selector-row">
                  <input
                    value={knowledgeKeyword}
                    onChange={(event) => setKnowledgeKeyword(event.target.value)}
                    placeholder="按标题或来源搜索文档"
                  />
                  <ActionButton variant="soft" onClick={() => void loadKnowledgeDocuments()}>
                    搜索
                  </ActionButton>
                </div>
                <span className="ai-muted">当前共有 {knowledgeTotal} 份知识文档，活跃文档 {stats?.activeDocumentCount ?? 0} 份，切片 {stats?.segmentCount ?? 0} 条。</span>
              </div>

              {knowledgeDocs.length === 0 && <Notice type="info">目前还没有知识文档，先录入一份文本或上传一个文件试试。</Notice>}

              {knowledgeDocs.map((document) => (
                <article key={document.id} className="admin-row ai-admin-row">
                  <button type="button" className="ai-admin-row-main" onClick={() => void openKnowledgeDetail(document.id)}>
                    <strong>{document.title}</strong>
                    <span>{sourceTypeText(document.sourceType)} · {document.sourceName || "未填写来源"} · {statusText(document.status)}</span>
                    <span>{indexStatusText(document.indexStatus)} · 切片 {document.segmentCount} · 更新时间 {formatDate(document.updateTime)}</span>
                  </button>
                  <div className="ai-admin-actions">
                    <ActionButton variant="ghost" busy={knowledgeBusy} onClick={() => void handleReindexDocument(document.id)}>重建索引</ActionButton>
                    <ActionButton variant="soft" busy={knowledgeBusy} onClick={() => void handleDisableDocument(document.id)}>停用</ActionButton>
                    <ActionButton variant="danger" busy={knowledgeBusy} onClick={() => void handleDeleteDocument(document.id)}>删除</ActionButton>
                  </div>
                </article>
              ))}
            </div>

            <aside className="game-detail-panel">
              <p className="eyebrow">文档详情</p>
              {!selectedDetail && <p>点击左侧文档可以查看它的元数据、索引状态和哈希，用来判断这份知识是否已经正确入库。</p>}
              {selectedDetail && (
                <div className="ai-detail-stack">
                  <h2>{selectedDetail.title}</h2>
                  <span>{sourceTypeText(selectedDetail.sourceType)} · {selectedDetail.sourceName || "未填写来源"}</span>
                  <span>{statusText(selectedDetail.status)} · {indexStatusText(selectedDetail.indexStatus)}</span>
                  <span>切片数 {selectedDetail.segmentCount} · 创建人 {selectedDetail.createdBy ?? "-"}</span>
                  <span>Hash {selectedDetail.contentHash || "-"}</span>
                  <span>创建时间 {formatDate(selectedDetail.createTime)}</span>
                  <span>更新时间 {formatDate(selectedDetail.updateTime)}</span>
                </div>
              )}
            </aside>
          </div>
        </div>
      )}

      {tab === "debug" && isAdmin && (
        <div className="ai-debug-layout">
          <div className="game-overview-grid">
            <article className="game-summary-card">
              <span>知识文档</span>
              <strong>{stats?.documentCount ?? 0}</strong>
              <p>文档级元数据存 MySQL，便于禁用、重建和审计。</p>
            </article>
            <article className="game-summary-card">
              <span>活跃文档</span>
              <strong>{stats?.activeDocumentCount ?? 0}</strong>
              <p>只有启用状态的文档才会继续参与检索和回答。</p>
            </article>
            <article className="game-summary-card">
              <span>检索切片</span>
              <strong>{stats?.segmentCount ?? 0}</strong>
              <p>所有切片在 ES 里支持关键词 BM25 和语义向量双路召回。</p>
            </article>
          </div>

          <article className="form-card ai-admin-card">
            <h2>检索调试</h2>
            <form className="catalog-search-row" onSubmit={handleDebugSearch}>
              <textarea
                value={debugQuery}
                onChange={(event) => setDebugQuery(event.target.value)}
                rows={4}
                placeholder="输入一个真实问题，查看 BM25、语义命中和最终混排结果。"
              />
              <div className="shop-selector-row">
                <input
                  type="number"
                  min={1}
                  max={12}
                  value={debugTopK}
                  onChange={(event) => setDebugTopK(Number(event.target.value) || 6)}
                />
                <ActionButton type="submit" busy={debugBusy}>执行调试</ActionButton>
              </div>
            </form>
          </article>

          <div className="ai-debug-grid">
            <article className="admin-list">
              <h3>BM25 命中</h3>
              {(debugResult?.bm25Hits ?? []).length === 0 && <p className="ai-muted">暂时还没有结果。</p>}
              {(debugResult?.bm25Hits ?? []).map((hit) => (
                <article key={`bm25-${hit.segmentId}`} className="ai-hit-card">
                  <strong>{hit.title}</strong>
                  <span>片段 #{hit.segmentOrder + 1} · BM25 {hit.bm25Score?.toFixed(3) ?? "0.000"}</span>
                  <p>{toPreview(hit.contentPreview)}</p>
                </article>
              ))}
            </article>

            <article className="admin-list">
              <h3>语义命中</h3>
              {(debugResult?.semanticHits ?? []).length === 0 && <p className="ai-muted">暂时还没有结果。</p>}
              {(debugResult?.semanticHits ?? []).map((hit) => (
                <article key={`semantic-${hit.segmentId}`} className="ai-hit-card">
                  <strong>{hit.title}</strong>
                  <span>片段 #{hit.segmentOrder + 1} · Semantic {hit.semanticScore?.toFixed(3) ?? "0.000"}</span>
                  <p>{toPreview(hit.contentPreview)}</p>
                </article>
              ))}
            </article>

            <article className="admin-list">
              <h3>最终混排</h3>
              {(debugResult?.mergedHits ?? []).length === 0 && <p className="ai-muted">暂时还没有结果。</p>}
              {(debugResult?.mergedHits ?? []).map((hit) => (
                <article key={`merged-${hit.segmentId}`} className="ai-hit-card">
                  <strong>{hit.title}</strong>
                  <span>
                    片段 #{hit.segmentOrder + 1} · 综合分 {hit.finalScore?.toFixed(3) ?? "0.000"}
                    {" · "}BM25 {hit.bm25Score?.toFixed(3) ?? "0.000"}
                    {" · "}Semantic {hit.semanticScore?.toFixed(3) ?? "0.000"}
                  </span>
                  <p>{toPreview(hit.contentPreview)}</p>
                </article>
              ))}
            </article>
          </div>
        </div>
      )}
    </section>
  );
}
