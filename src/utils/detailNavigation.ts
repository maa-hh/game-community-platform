import type { IGameDetail, IGameListItem } from '@/types/game';
import type { LatestPostItem, PostDetailData } from '@/types/post';
import type { PostRefCard } from '@/types/content';
import type { FeedItemData } from '@/types/profile';
import {
  buildReturnNavigationState,
  type ReturnLocation,
  type ReturnNavigationState,
} from '@/utils/returnNavigation';

export interface DetailNavigationState extends ReturnNavigationState {
  /** 从列表卡片带入的帖子快照，用于详情首屏预渲染。 */
  postPreview?: LatestPostItem;
  /** 从游戏卡片带入的游戏快照，用于详情首屏预渲染。 */
  gamePreview?: IGameListItem;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function isLatestPostItem(value: unknown): value is LatestPostItem {
  return (
    isRecord(value) &&
    typeof value.id === 'string' &&
    typeof value.title === 'string' &&
    isRecord(value.author) &&
    typeof value.author.accountId === 'number' &&
    typeof value.author.nickname === 'string'
  );
}

function isGameListItem(value: unknown): value is IGameListItem {
  return (
    isRecord(value) &&
    typeof value.appId === 'number' &&
    typeof value.name === 'string'
  );
}

export function buildPostDetailNavigationState(
  location: ReturnLocation,
  item: LatestPostItem,
): DetailNavigationState {
  return {
    ...buildReturnNavigationState(location),
    postPreview: item,
  };
}

export function buildGameDetailNavigationState(
  location: ReturnLocation,
  item: IGameListItem,
): DetailNavigationState {
  return {
    ...buildReturnNavigationState(location),
    gamePreview: item,
  };
}

export function mapLatestPostToDetailPreview(
  item: LatestPostItem,
): PostDetailData {
  return {
    id: item.id,
    postType: item.postType,
    title: item.title,
    content: item.content || item.summary || '',
    images: item.images,
    coverUrl: item.coverUrl,
    videoUrl: item.videoUrl,
    refPost: item.refPost,
    tags: item.tags,
    gameTags: item.gameTags,
    author: item.author,
    createdAt: item.createdAt,
    stats: {
      viewCount: item.viewCount ?? 0,
      likeCount: item.likeCount ?? 0,
      commentCount: item.commentCount ?? 0,
      favoriteCount: item.favoriteCount ?? 0,
      shareCount: 0,
      liked: Boolean(item.liked),
      favorited: Boolean(item.favorited),
    },
  };
}

export function mapFeedItemToLatestPost(
  item: FeedItemData,
): LatestPostItem | undefined {
  if (!item.id) return undefined;
  return {
    id: item.id,
    postType: item.postType ?? 'image_text',
    title: item.title,
    summary: item.summary,
    content: item.content || item.summary || '',
    images: item.images?.length
      ? item.images
      : item.cover
        ? [item.cover]
        : undefined,
    coverUrl: item.coverUrl,
    videoUrl: item.videoUrl,
    refPost: item.refPost,
    author: item.author,
    createdAt: item.createdAt || '',
    tags: item.tags,
    gameTags: item.gameTags,
    viewCount: item.viewCount ?? 0,
    likeCount: item.likeCount ?? 0,
    commentCount: item.commentCount ?? 0,
    favoriteCount: 0,
    liked: item.liked,
    likePending: item.likePending,
  };
}

/** 动态里的引用卡也是一份可用的帖子快照。 */
export function mapPostRefToLatestPost(
  refPost: PostRefCard,
  id = refPost.id,
): LatestPostItem | undefined {
  if (refPost.unavailable || !id || id.startsWith('game-')) return undefined;
  const coverUrl = refPost.coverUrl;
  return {
    id,
    postType: refPost.postType,
    title: refPost.title,
    summary: refPost.summary,
    content: refPost.summary || '',
    images: coverUrl ? [coverUrl] : undefined,
    coverUrl,
    videoUrl: refPost.videoUrl,
    author: refPost.author,
    createdAt: '',
    viewCount: refPost.viewCount ?? 0,
    likeCount: refPost.likeCount ?? 0,
    commentCount: refPost.commentCount ?? 0,
    favoriteCount: 0,
    liked: refPost.liked,
    likePending: refPost.likePending,
  };
}

export function mapGameListItemToDetailPreview(
  item: IGameListItem,
): IGameDetail {
  return {
    appId: item.appId,
    name: item.name,
    coverUrl: item.coverUrl,
    tags: item.genres,
    developer: item.developer,
    publisher: item.publisher,
    releaseDate: item.releaseDate,
    steamReviewScore: item.steamScore,
    steamReviewCount: item.steamReviewCount,
    discussCount: item.discussCount,
    price: item.price,
    detailReady: false,
  };
}

export function readPostDetailPreview(
  state: unknown,
  id: string,
): PostDetailData | null {
  if (!isRecord(state) || !isLatestPostItem(state.postPreview)) return null;
  if (state.postPreview.id !== id) return null;
  return mapLatestPostToDetailPreview(state.postPreview);
}

export function readGameDetailPreview(
  state: unknown,
  appId: number,
): IGameDetail | null {
  if (!isRecord(state) || !isGameListItem(state.gamePreview)) return null;
  if (state.gamePreview.appId !== appId) return null;
  return mapGameListItemToDetailPreview(state.gamePreview);
}
