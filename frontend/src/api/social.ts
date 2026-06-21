import { ArticleSummary } from "./content";
import { request, requestEnvelope } from "./client";

export type ArticleStats = {
  articleId: number;
  likeCount: number;
  commentCount: number;
  viewCount: number;
  liked: boolean;
};

export type CommentVO = {
  id: number;
  articleId: number;
  userId: number;
  username: string;
  avatar?: string;
  content: string;
  likeCount: number;
  replyCount: number;
  liked: boolean;
  createTime?: string;
};

export type ReplyVO = {
  id: number;
  commentId: number;
  articleId: number;
  userId: number;
  username: string;
  avatar?: string;
  replyToUserId?: number;
  replyToUsername?: string;
  content: string;
  likeCount: number;
  liked: boolean;
  createTime?: string;
};

export type FollowUser = {
  userId: number;
  username: string;
  avatar?: string;
  signature?: string;
  createTime?: string;
};

export type PageEnvelope<T> = {
  code: number;
  message: string;
  data: T[];
  page: number;
  size: number;
  total: number;
};

export type BrowseHistory = {
  articleId: number;
  article?: ArticleSummary;
  browseTime?: string;
};

export const ReportTargetType = {
  ARTICLE: 1,
  COMMENT: 2,
  REPLY: 3,
  USER: 4
} as const;

export const socialApi = {
  getArticleStats: (articleId: number) => request<ArticleStats>(`/social/article/count/${articleId}`),
  getArticleStatsBatch: (articleIds: number[]) => {
    const params = new URLSearchParams();
    articleIds.forEach((id) => params.append("articleIds", String(id)));
    return request<ArticleStats[]>(`/social/article/counts?${params.toString()}`);
  },
  viewArticle: (articleId: number) => request<ArticleSummary>(`/social/article/${articleId}`),
  listFeed: (payload?: { before?: string; size?: number }) => {
    const params = new URLSearchParams();
    if (payload?.before) {
      params.set("before", payload.before);
    }
    params.set("size", String(payload?.size ?? 12));
    return requestEnvelope<ArticleSummary[]>(`/social/feed?${params.toString()}`) as Promise<PageEnvelope<ArticleSummary>>;
  },
  likeArticle: (articleId: number) => request<void>(`/social/like/article/${articleId}`, { method: "POST" }),
  unlikeArticle: (articleId: number) => request<void>(`/social/like/article/${articleId}`, { method: "DELETE" }),
  listComments: (articleId: number, page = 1, size = 20) =>
    requestEnvelope<CommentVO[]>(`/social/comment/list/${articleId}?page=${page}&size=${size}`) as Promise<PageEnvelope<CommentVO>>,
  getCommentDetail: (commentId: number) => request<CommentVO>(`/social/comment/${commentId}`),
  addComment: (articleId: number, content: string) =>
    request<number>("/social/comment", { method: "POST", body: { articleId, content } }),
  deleteComment: (commentId: number) => request<void>(`/social/comment/${commentId}`, { method: "DELETE" }),
  likeComment: (commentId: number) => request<void>(`/social/like/comment/${commentId}`, { method: "POST" }),
  unlikeComment: (commentId: number) => request<void>(`/social/like/comment/${commentId}`, { method: "DELETE" }),
  listReplies: (commentId: number, page = 1, size = 20) =>
    requestEnvelope<ReplyVO[]>(`/social/reply/list/${commentId}?page=${page}&size=${size}`) as Promise<PageEnvelope<ReplyVO>>,
  getReplyDetail: (replyId: number) => request<ReplyVO>(`/social/reply/${replyId}`),
  addReply: (commentId: number, content: string, replyToUserId?: number) =>
    request<number>("/social/reply", { method: "POST", body: { commentId, content, replyToUserId } }),
  likeReply: (replyId: number) => request<void>(`/social/like/reply/${replyId}`, { method: "POST" }),
  unlikeReply: (replyId: number) => request<void>(`/social/like/reply/${replyId}`, { method: "DELETE" }),
  follow: (targetUserId: number) => request<void>(`/social/follow/${targetUserId}`, { method: "POST" }),
  unfollow: (targetUserId: number) => request<void>(`/social/follow/${targetUserId}`, { method: "DELETE" }),
  black: (targetUserId: number) => request<void>(`/social/follow/black/${targetUserId}`, { method: "POST" }),
  unblack: (targetUserId: number) => request<void>(`/social/follow/black/${targetUserId}`, { method: "DELETE" }),
  checkFollowing: (targetUserId: number) => request<boolean>(`/social/follow/check/${targetUserId}`),
  checkBlack: (targetUserId: number) => request<boolean>(`/social/follow/black/check/${targetUserId}`),
  countRelation: (targetUserId: number) => request<{ following: number; fans: number }>(`/social/follow/count/${targetUserId}`),
  listFollowing: (userId?: number) => {
    const suffix = userId ? `?userId=${userId}` : "";
    return requestEnvelope<FollowUser[]>(`/social/follow/list${suffix}`) as Promise<PageEnvelope<FollowUser>>;
  },
  listFans: (userId?: number) => {
    const suffix = userId ? `?userId=${userId}` : "";
    return requestEnvelope<FollowUser[]>(`/social/follow/fans${suffix}`) as Promise<PageEnvelope<FollowUser>>;
  },
  listBlack: () => requestEnvelope<FollowUser[]>("/social/follow/black/list") as Promise<PageEnvelope<FollowUser>>,
  listBrowseHistory: () => requestEnvelope<BrowseHistory[]>("/social/browse/history") as Promise<PageEnvelope<BrowseHistory>>,
  listLikedArticles: () => requestEnvelope<ArticleSummary[]>("/social/like/article/list") as Promise<PageEnvelope<ArticleSummary>>
  ,
  createReport: (payload: { targetType: number; targetId: number; reason: string }) =>
    request<number>("/report", { method: "POST", body: payload })
};
