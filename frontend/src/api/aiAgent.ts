import { request, requestEnvelope } from "./client";

export type AiChatMessage = {
  role: string;
  content: string;
  time?: string;
};

export type AiKnowledgeSearchHit = {
  segmentId: string;
  documentId: number;
  title: string;
  contentPreview: string;
  segmentOrder: number;
  bm25Score?: number;
  semanticScore?: number;
  finalScore?: number;
};

export type AiChatResponse = {
  answer: string;
  references: AiKnowledgeSearchHit[];
};

export type AiKnowledgeDocument = {
  id: number;
  title: string;
  sourceType: number;
  sourceName?: string;
  status: number;
  indexStatus: number;
  segmentCount: number;
  createTime?: string;
  updateTime?: string;
};

export type AiKnowledgeDetail = AiKnowledgeDocument & {
  contentHash?: string;
  createdBy?: number;
  updatedBy?: number;
};

export type AiKnowledgeStats = {
  documentCount: number;
  activeDocumentCount: number;
  segmentCount: number;
};

export type AiKnowledgeDebug = {
  query: string;
  bm25Hits: AiKnowledgeSearchHit[];
  semanticHits: AiKnowledgeSearchHit[];
  mergedHits: AiKnowledgeSearchHit[];
};

export type PageResult<T> = {
  data: T[];
  page: number;
  size: number;
  total: number;
};

export const aiAgentApi = {
  chat: (sessionId: string, message: string) =>
    request<AiChatResponse>("/ai/chat", { method: "POST", body: { sessionId, message } }),
  getSessionMessages: (sessionId: string) =>
    request<AiChatMessage[]>(`/ai/chat/session/${encodeURIComponent(sessionId)}/messages`),
  clearSession: (sessionId: string) =>
    request<void>(`/ai/chat/session/${encodeURIComponent(sessionId)}`, { method: "DELETE" }),
  addKnowledgeText: (payload: { title: string; content: string; sourceName?: string }) =>
    request<AiKnowledgeDocument>("/ai/knowledge/text", { method: "POST", body: payload }),
  addKnowledgeFile: (file: File) => {
    const form = new FormData();
    form.append("file", file);
    return request<AiKnowledgeDocument>("/ai/knowledge/file", { method: "POST", body: form });
  },
  pageDocuments: (payload?: { page?: number; size?: number; keyword?: string; status?: number }) => {
    const params = new URLSearchParams({
      page: String(payload?.page ?? 1),
      size: String(payload?.size ?? 10)
    });
    if (payload?.keyword) {
      params.set("keyword", payload.keyword);
    }
    if (payload?.status !== undefined) {
      params.set("status", String(payload.status));
    }
    return requestEnvelope<AiKnowledgeDocument[]>(`/ai/knowledge/page?${params.toString()}`) as unknown as Promise<PageResult<AiKnowledgeDocument>>;
  },
  getDocument: (documentId: number) =>
    request<AiKnowledgeDetail>(`/ai/knowledge/${documentId}`),
  disableDocument: (documentId: number) =>
    request<void>(`/ai/knowledge/${documentId}/disable`, { method: "PUT" }),
  deleteDocument: (documentId: number) =>
    request<void>(`/ai/knowledge/${documentId}`, { method: "DELETE" }),
  reindexDocument: (documentId: number) =>
    request<void>(`/ai/knowledge/${documentId}/reindex`, { method: "POST" }),
  debugSearch: (query: string, topK = 6) =>
    request<AiKnowledgeDebug>("/ai/knowledge/debug/search", { method: "POST", body: { query, topK } }),
  getStats: () =>
    request<AiKnowledgeStats>("/ai/knowledge/debug/stats")
};
