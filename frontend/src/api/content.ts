import { request } from "./client";

export type ContentCategory = {
  id: number;
  name: string;
  description?: string;
  status: number;
  sort: number;
};

export type ArticleSummary = {
  id: number;
  userId: number;
  title: string;
  summary?: string;
  coverUrl?: string;
  categoryId: number;
  status: number;
  auditMessage?: string;
  scheduledPublishTime?: string;
  publishedTime?: string;
  createTime?: string;
  updateTime?: string;
};

export type ArticleDetail = ArticleSummary & {
  content?: string;
  contentParagraphs?: Record<string, string>;
  imageUrls?: string[];
};

export type ArticleContent = {
  articleId: number;
  content: string;
  contentParagraphs?: Record<string, string>;
  imageUrls?: string[];
  userId: number;
  createTime?: string;
  updateTime?: string;
};

export type ArticlePayload = {
  title: string;
  summary?: string;
  content: string;
  contentParagraphs?: Record<string, string>;
  coverUrl?: string;
  imageUrls?: string[];
  categoryId: number;
  status?: number;
  scheduledPublishTime?: string | null;
};

export const contentApi = {
  listCategories: () => request<ContentCategory[]>("/category/listEnabled"),
  listLatest: (payload?: { categoryId?: number; size?: number }) => {
    const params = new URLSearchParams();
    if (payload?.categoryId) {
      params.set("categoryId", String(payload.categoryId));
    }
    params.set("size", String(payload?.size ?? 12));
    return request<ArticleSummary[]>(`/article/latest?${params.toString()}`);
  },
  listMore: (payload: { lastId: number; categoryId?: number; size?: number }) => {
    const params = new URLSearchParams({
      lastId: String(payload.lastId),
      size: String(payload.size ?? 12)
    });
    if (payload.categoryId) {
      params.set("categoryId", String(payload.categoryId));
    }
    return request<ArticleSummary[]>(`/article/more?${params.toString()}`);
  },
  getArticleDetail: (id: number) => request<ArticleDetail>(`/article/${id}`),
  getArticleContent: (id: number) => request<ArticleContent>(`/article/${id}/content`),
  listAuthorPublished: (authorId: number, size = 20) =>
    request<ArticleSummary[]>(`/article/author/${authorId}/published?size=${size}`),
  getMyArticles: () => request<ArticleSummary[]>("/article/my"),
  createArticle: (payload: ArticlePayload) => request<number>("/article", { method: "POST", body: payload }),
  updateArticle: (id: number, payload: ArticlePayload) => request<number>(`/article/${id}`, { method: "PUT", body: payload }),
  deleteArticle: (id: number) => request<void>(`/article/${id}`, { method: "DELETE" }),
  uploadFiles: async (files: File[]) => {
    const form = new FormData();
    for (const file of files) {
      form.append("files", file);
    }
    return request<string[]>("/file/upload", { method: "POST", body: form });
  }
};
