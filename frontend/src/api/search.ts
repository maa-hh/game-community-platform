import { request } from "./client";
import { ArticleSummary } from "./content";

export type ArticleSearchItem = ArticleSummary & {
  username?: string;
  avatar?: string;
  categoryName?: string;
};

export type SearchHistory = {
  id: number;
  userId: number;
  keyword: string;
  createTime?: string;
  updateTime?: string;
};

export type SuggestDocument = {
  id?: number;
  suggest: string;
  suggestNgram?: string;
};

export const searchApi = {
  searchArticles: (payload: { keyword?: string; categoryId?: number | null; page?: number; size?: number; sort?: "relevance" | "latest" }) => {
    const params = new URLSearchParams();
    if (payload.keyword?.trim()) {
      params.set("keyword", payload.keyword.trim());
    }
    if (payload.categoryId) {
      params.set("categoryId", String(payload.categoryId));
    }
    params.set("page", String(payload.page ?? 1));
    params.set("size", String(payload.size ?? 12));
    params.set("sort", payload.sort ?? "relevance");
    return request<ArticleSearchItem[]>(`/search/article?${params.toString()}`);
  },
  suggest: (prefix: string) => request<SuggestDocument[]>(`/search/suggest?prefix=${encodeURIComponent(prefix)}`),
  listHistory: () => request<SearchHistory[]>("/search/record/list"),
  addHistory: (keyword: string) => request<void>(`/search/record?keyword=${encodeURIComponent(keyword)}`, { method: "POST" }),
  deleteHistory: (id: number) => request<void>(`/search/record/${id}`, { method: "DELETE" }),
  clearHistory: () => request<number>("/search/record/clear", { method: "DELETE" })
};
