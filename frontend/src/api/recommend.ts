import { request } from "./client";

export type HotArticle = {
  id: number;
  userId: number;
  title: string;
  summary?: string;
  coverUrl?: string;
  categoryId: number;
  categoryName?: string;
  authorName?: string;
  authorAvatar?: string;
  likeCount: number;
  commentCount: number;
  viewCount: number;
  liked: boolean;
  hotScore: number;
  publishedTime?: string;
  createTime?: string;
  updateTime?: string;
};

export const recommendApi = {
  listHot: (payload?: { page?: number; size?: number; categoryId?: number | null }) => {
    const params = new URLSearchParams({
      page: String(payload?.page ?? 1),
      size: String(payload?.size ?? 12)
    });
    if (payload?.categoryId) {
      return request<HotArticle[]>(`/hot-article/category/${payload.categoryId}?${params.toString()}`);
    }
    return request<HotArticle[]>(`/hot-article/list?${params.toString()}`);
  }
};
