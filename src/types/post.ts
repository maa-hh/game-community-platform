import type {
  ContentCardPostType,
  ContentCardTag,
  PostRefCard,
  PostStats,
} from '@/types/content';
import type { IGameTag } from '@/types/game';

export type { ContentCardPostType, ContentCardTag, PostRefCard, PostStats };

/** 帖子作者（对外仅 accountId） */
export interface PostAuthor {
  accountId: number;
  nickname: string;
  avatar?: string;
}

export interface PostDetailData {
  id: string;
  postType: ContentCardPostType;
  title: string;
  content: string;
  contentHtml?: string;
  images?: string[];
  bodyImages?: string[];
  coverUrl?: string;
  videoUrl?: string;
  categoryName?: string;
  tags?: ContentCardTag[];
  gameTags?: IGameTag[];
  author: PostAuthor;
  createdAt: string;
  status?: number;
  refPost?: PostRefCard;
  stats: PostStats;
  followedAuthor?: boolean;
}

export interface PostReply {
  id: string;
  accountId: number;
  nickname: string;
  avatar?: string;
  replyToAccountId?: number;
  replyToNickname?: string;
  content: string;
  likeCount: number;
  liked: boolean;
  createdAt: string;
}

export interface PostComment {
  id: string;
  accountId: number;
  nickname: string;
  avatar?: string;
  content: string;
  likeCount: number;
  liked: boolean;
  replyCount: number;
  createdAt: string;
  replies: PostReply[];
}

export interface LatestPostItem {
  id: string;
  postType: ContentCardPostType;
  title: string;
  /** 卡片摘要：用户填写或正文截取 */
  summary?: string;
  /** @deprecated 请使用 summary */
  content: string;
  images?: string[];
  coverUrl?: string;
  videoUrl?: string;
  refPost?: PostRefCard;
  author: PostAuthor;
  createdAt: string;
  tags?: ContentCardTag[];
  gameTags?: IGameTag[];
  viewCount: number;
  likeCount: number;
  commentCount: number;
  favoriteCount: number;
  liked?: boolean;
  favorited?: boolean;
  /** 游标分页用（ISO 时间，对应 publishedTime/createTime） */
  sortTime?: string;
  /** 热榜名次 */
  rank?: number;
  /** 热度分 */
  hotScore?: number;
}
