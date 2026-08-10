import type { IGameTag } from '@/types/game';

/** 帖子展示类型（与发帖三模式对齐） */
export type ContentCardPostType = 'image_text' | 'article' | 'video' | 'repost';

/** 展示层作者（列表卡 / 详情可共用，仅 accountId） */
export interface Author {
  accountId: number;
  nickname: string;
  avatar?: string;
}

export type ContentCardAuthor = Author;

export interface ContentCardTag {
  text: string;
  icon?: string;
}

/** 转发引用原帖摘要（ShareCard / 列表卡共用） */
export type PostRefUnavailableReason = 'deleted' | 'offline' | 'unavailable';

export interface PostRefCard {
  id: string;
  title: string;
  summary?: string;
  coverUrl?: string;
  videoUrl?: string;
  postType: ContentCardPostType;
  author: ContentCardAuthor;
  viewCount?: number;
  commentCount?: number;
  likeCount?: number;
  liked?: boolean;
  /** 原帖不可访问（删除/下架/拉取失败） */
  unavailable?: boolean;
  unavailableReason?: PostRefUnavailableReason;
  unavailableMessage?: string;
}

export interface ContentCardData {
  id?: string;
  author: ContentCardAuthor;
  title: string;
  /** 卡片摘要：用户填写或正文截取，标题下最多展示两行 */
  summary?: string;
  /** 转发附言等正文片段（非卡片摘要） */
  content: string;
  images?: string[];
  coverUrl?: string;
  videoUrl?: string;
  postType?: ContentCardPostType;
  refPost?: PostRefCard;
  /** @deprecated 请使用 postType */
  mediaType?: 'image' | 'video';
  tags?: ContentCardTag[];
  /** 关联游戏标签（可点击跳转游戏详情） */
  gameTags?: IGameTag[];
  viewCount?: number;
  commentCount?: number;
  likeCount?: number;
  liked?: boolean;
  createdAt?: string;
  /** 热榜名次角标 */
  rank?: number;
  /** 热度分展示 */
  hotScore?: number;
}

export interface PostStats {
  viewCount: number;
  likeCount: number;
  commentCount: number;
  favoriteCount: number;
  shareCount: number;
  liked: boolean;
  favorited: boolean;
}
