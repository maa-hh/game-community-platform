import { mapNumericPostType } from '@/utils/postType';
import { buildCategoryTags, resolvePostDisplayTags } from '@/utils/categoryTag';
import { formatCardTime, formatDateTime } from '@/utils/formatTime';
import type {
  LatestPostItem,
  PostAuthor,
  PostComment,
  PostDetailData,
  PostRefCard,
  PostReply,
} from '@/types/post';
import { mapGameTagsFromRaw, type IGameTagRaw } from '@/utils/mapGameTag';
import { countBodyImageMarkers } from '@/utils/bodyImageMarker';
import { resolveDisplayCommentCount } from '@/utils/commentCount';
import { resolvePostCardSummary } from '@/utils/postSummary';
import { attachRefPostStats, resolveRepostRefPost } from '@/utils/refPostCard';
import {
  buildGameRepostRefPost,
  isGameRepostArticle,
  resolveDisplayPostType,
  sanitizeGameShareContent,
} from '@/utils/gameRepost';

export interface IArticleDetailRaw {
  id: number;
  publicId: string;
  authorAccountId: number;
  username?: string;
  avatar?: string;
  title: string;
  summary?: string;
  coverUrl?: string;
  videoUrl?: string;
  postType?: number;
  refArticleId?: string;
  refArticle?: {
    id: string;
    title: string;
    summary?: string;
    coverUrl?: string;
    videoUrl?: string;
    postType?: number;
    authorAccountId?: number;
    username?: string;
    avatar?: string;
  };
  categoryId?: number;
  categoryIds?: number[];
  categoryNames?: string[];
  gameTags?: IGameTagRaw[];
  status?: number;
  content?: string;
  contentHtml?: string;
  contentParagraphs?: Record<string, string>;
  imageUrls?: string[];
  publishedTime?: string;
  createTime?: string;
  updateTime?: string;
}

export interface IArticleRaw {
  id: number;
  publicId: string;
  authorAccountId: number;
  username?: string;
  avatar?: string;
  title: string;
  summary?: string;
  coverUrl?: string;
  videoUrl?: string;
  postType?: number;
  refArticleId?: string;
  refArticle?: IArticleDetailRaw['refArticle'];
  categoryId?: number;
  categoryIds?: number[];
  categoryNames?: string[];
  gameTags?: IGameTagRaw[];
  content?: string;
  contentParagraphs?: Record<string, string>;
  status?: number;
  publishedTime?: string;
  createTime?: string;
  updateTime?: string;
  actionTime?: string;
}

export interface IArticleStatsRaw {
  articleId: string | number;
  publicId?: string;
  likeCount?: number;
  commentCount?: number;
  replyCount?: number;
  favoriteCount?: number;
  shareCount?: number;
  viewCount?: number;
  liked?: boolean;
  favorited?: boolean;
}

export interface ICommentRaw {
  id: number;
  articleId: number;
  accountId: number;
  username: string;
  avatar?: string;
  content: string;
  likeCount?: number;
  replyCount?: number;
  liked?: boolean;
  createTime?: string;
}

export interface IReplyRaw {
  id: number;
  commentId: number;
  articleId?: number;
  accountId: number;
  username: string;
  avatar?: string;
  replyToAccountId?: number;
  replyToUsername?: string;
  content: string;
  likeCount?: number;
  liked?: boolean;
  createTime?: string;
}

export { formatDateTime } from '@/utils/formatTime';

export function authorFrom(
  accountId: number,
  username?: string | null,
  avatar?: string | null,
): PostAuthor {
  const trimmed = username?.trim();
  const nickname = trimmed || '';
  return {
    accountId,
    nickname,
    avatar: avatar || undefined,
  };
}

export function attachRefStats(
  ref: PostRefCard,
  stats?: IArticleStatsRaw | null,
): PostRefCard {
  return attachRefPostStats(ref, stats);
}

export function mapRefPost(
  ref?: IArticleDetailRaw['refArticle'],
): PostRefCard | undefined {
  if (!ref?.id) return undefined;
  const postType = mapNumericPostType(ref.postType);
  return {
    id: String(ref.id),
    title: ref.title,
    summary: ref.summary,
    coverUrl: ref.coverUrl,
    videoUrl: ref.videoUrl,
    postType: postType === 'repost' ? 'image_text' : postType,
    author: authorFrom(ref.authorAccountId || 0, ref.username, ref.avatar),
  };
}

function mediaUrl(value?: string | null): string | undefined {
  if (!value?.trim()) return undefined;
  return value;
}

/** 解析文章封面：图文优先 coverUrl，否则取首张封面图；视频用 coverUrl */
export function resolveArticleCoverUrl(input: {
  postType?: number;
  coverUrl?: string | null;
  imageUrls?: string[] | null;
}): string | undefined {
  const cover = mediaUrl(input.coverUrl);
  const postType = mapNumericPostType(input.postType);
  if (postType === 'video') {
    return cover;
  }
  const gallery = dedupeUrls([
    ...(input.coverUrl ? [input.coverUrl] : []),
    ...(input.imageUrls || []),
  ]);
  const firstImage = gallery.map((url) => mediaUrl(url)).find(Boolean);
  return cover || firstImage;
}

function paragraphsToHtml(
  paragraphs?: Record<string, string>,
): string | undefined {
  if (!paragraphs) return undefined;
  const keys = Object.keys(paragraphs).sort((a, b) => {
    const na = Number(a.replace(/\D/g, '')) || 0;
    const nb = Number(b.replace(/\D/g, '')) || 0;
    return na - nb;
  });
  if (keys.length === 0) return undefined;
  return keys
    .map((k) => {
      const text = paragraphs[k] || '';
      return `<p>${text
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')}</p>`;
    })
    .join('');
}

function dedupeUrls(urls: string[]): string[] {
  const seen = new Set<string>();
  return urls.filter((url) => {
    const key = url.trim();
    if (!key || seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

/** 图文帖封面图：新模型 imageUrls 全是封面；旧帖兼容内嵌图标记 */
function resolveArticleGalleryImages(
  raw: Pick<IArticleDetailRaw, 'postType' | 'coverUrl' | 'imageUrls'>,
  contentText: string,
): { galleryImages: string[]; bodyImages: string[] } {
  const allImages = raw.imageUrls?.filter(Boolean) || [];
  const markerCount = countBodyImageMarkers(contentText);
  const isLegacyArticle = raw.postType === 2;

  if (markerCount > 0 && allImages.length >= markerCount) {
    return {
      bodyImages: allImages.slice(-markerCount),
      galleryImages: dedupeUrls([
        ...(raw.coverUrl ? [raw.coverUrl] : []),
        ...allImages.slice(0, allImages.length - markerCount),
      ]),
    };
  }

  if (isLegacyArticle && allImages.length > 0) {
    return {
      bodyImages: allImages,
      galleryImages: dedupeUrls(raw.coverUrl ? [raw.coverUrl] : []),
    };
  }

  const galleryImages = dedupeUrls([
    ...(raw.coverUrl ? [raw.coverUrl] : []),
    ...allImages,
  ]);
  return {
    galleryImages,
    bodyImages: [],
  };
}
export function mapArticleDetail(
  raw: IArticleDetailRaw,
  stats?: IArticleStatsRaw | null,
  extras?: {
    categoryName?: string;
    followedAuthor?: boolean;
    categoryMap?: Map<number, import('@/service/content').ICategory>;
  },
): PostDetailData {
  const postType = resolveDisplayPostType(raw);
  const isLegacyArticle = raw.postType === 2;
  const contentText = isGameRepostArticle(raw)
    ? sanitizeGameShareContent(raw)
    : raw.content || raw.summary || '';
  const { galleryImages, bodyImages } = resolveArticleGalleryImages(
    raw,
    contentText,
  );

  const contentHtml =
    raw.contentHtml ||
    (isLegacyArticle ? paragraphsToHtml(raw.contentParagraphs) : undefined);

  let html = contentHtml;
  if (isLegacyArticle && !html && raw.content) {
    html = `<p>${raw.content
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/\n/g, '<br/>')}</p>`;
  }

  const categoryMap = extras?.categoryMap ?? new Map();
  const categoryTags = resolvePostDisplayTags(
    buildCategoryTags(raw, categoryMap),
  );

  return {
    id: raw.publicId,
    postType,
    title: raw.title,
    content: contentText,
    contentHtml: html,
    images: galleryImages.length > 0 ? galleryImages : undefined,
    bodyImages: bodyImages.length > 0 ? bodyImages : undefined,
    coverUrl: raw.coverUrl,
    videoUrl: raw.videoUrl,
    categoryName: extras?.categoryName,
    tags: categoryTags.length > 0 ? categoryTags : undefined,
    gameTags: resolveArticleGameTags(raw),
    author: authorFrom(raw.authorAccountId, raw.username, raw.avatar),
    createdAt: formatDateTime(raw.publishedTime || raw.createTime),
    status: raw.status,
    refPost:
      mapRefPost(raw.refArticle) ??
      (postType === 'repost' && !raw.refArticleId
        ? buildGameRepostRefPost({
            gameTags: raw.gameTags,
            contentParagraphs: raw.contentParagraphs,
            content: raw.content,
            summary: raw.summary,
          })
        : undefined),
    stats: {
      viewCount: Number(stats?.viewCount || 0),
      likeCount: Number(stats?.likeCount || 0),
      commentCount: resolveDisplayCommentCount(stats),
      favoriteCount: Number(stats?.favoriteCount || 0),
      shareCount: Number(stats?.shareCount || 0),
      liked: Boolean(stats?.liked),
      favorited: Boolean(stats?.favorited),
    },
    followedAuthor: extras?.followedAuthor,
  };
}

export function mapArticleToLatest(
  raw: IArticleRaw,
  author: PostAuthor,
  stats?: IArticleStatsRaw | null,
  refPost?: PostRefCard,
  categoryMap: Map<number, import('@/service/content').ICategory> = new Map(),
): LatestPostItem {
  const postType = resolveDisplayPostType(raw);
  const cover = mediaUrl(raw.coverUrl);
  const videoUrl = mediaUrl(raw.videoUrl);
  const categoryTags = buildCategoryTags(raw, categoryMap);
  const summary = resolvePostCardSummary({ summary: raw.summary });
  const cleanSummary =
    sanitizeGameShareContent({ summary: raw.summary }) ||
    sanitizeGameShareContent({ content: raw.content }) ||
    summary;
  const repostQuote = isGameRepostArticle(raw)
    ? sanitizeGameShareContent(raw)
    : sanitizeGameShareContent({ content: raw.content }) || cleanSummary;
  const resolvedRefPost =
    postType === 'repost'
      ? raw.refArticleId
        ? resolveRepostRefPost(raw.refArticleId, refPost)
        : (refPost ??
          buildGameRepostRefPost({
            gameTags: raw.gameTags,
            contentParagraphs: raw.contentParagraphs,
            content: raw.content,
            summary: raw.summary,
          }))
      : undefined;
  const cardSummary =
    postType === 'repost' && isGameRepostArticle(raw)
      ? repostQuote || cleanSummary
      : cleanSummary;
  const gameTags = resolveArticleGameTags(raw);
  return {
    id: raw.publicId,
    postType,
    title: raw.title,
    summary: cardSummary,
    content: postType === 'repost' ? repostQuote : cleanSummary,
    coverUrl: postType === 'video' ? cover : undefined,
    videoUrl: postType === 'video' ? videoUrl : undefined,
    refPost: resolvedRefPost,
    images: cover ? [cover] : undefined,
    gameTags,
    tags: categoryTags.length > 0 ? categoryTags : undefined,
    author,
    createdAt: formatCardTime(raw.publishedTime || raw.createTime),
    sortTime: raw.publishedTime || raw.createTime,
    viewCount: Number(stats?.viewCount || 0),
    likeCount: Number(stats?.likeCount || 0),
    commentCount: resolveDisplayCommentCount(stats),
    favoriteCount: Number(stats?.favoriteCount || 0),
    liked: Boolean(stats?.liked),
    favorited: Boolean(stats?.favorited),
  };
}

export function resolveArticleGameTags(
  raw: Pick<
    IArticleRaw,
    'content' | 'summary' | 'contentParagraphs' | 'gameTags'
  >,
) {
  const mapped = mapGameTagsFromRaw(raw.gameTags) || [];
  return mapped.length > 0 ? mapped : undefined;
}

export function mapArticleToFeedItem(
  raw: IArticleRaw,
  author: PostAuthor,
  stats?: IArticleStatsRaw | null,
  refPost?: PostRefCard,
) {
  const postType = resolveDisplayPostType(raw);
  const cover = mediaUrl(raw.coverUrl);
  const videoUrl = mediaUrl(raw.videoUrl);
  const summary = resolvePostCardSummary({ summary: raw.summary });
  const cleanSummary =
    sanitizeGameShareContent({ summary: raw.summary }) ||
    sanitizeGameShareContent({ content: raw.content }) ||
    summary;
  const repostQuote = isGameRepostArticle(raw)
    ? sanitizeGameShareContent(raw)
    : sanitizeGameShareContent({ content: raw.content }) || cleanSummary;
  const displaySummary =
    postType === 'repost' && isGameRepostArticle(raw)
      ? repostQuote || cleanSummary
      : cleanSummary;
  return {
    id: raw.publicId,
    author: {
      accountId: author.accountId,
      nickname: author.nickname,
      avatar: author.avatar,
    },
    title: raw.title,
    summary: displaySummary,
    content: postType === 'repost' ? repostQuote : cleanSummary,
    postType,
    images: cover ? [cover] : undefined,
    coverUrl: postType === 'video' ? cover : undefined,
    videoUrl: postType === 'video' ? videoUrl : undefined,
    refPost:
      postType === 'repost'
        ? raw.refArticleId
          ? resolveRepostRefPost(raw.refArticleId, refPost)
          : buildGameRepostRefPost({
              gameTags: raw.gameTags,
              contentParagraphs: raw.contentParagraphs,
              content: raw.content,
              summary: raw.summary,
            })
        : undefined,
    gameTags: resolveArticleGameTags(raw),
    createdAt: formatCardTime(raw.publishedTime || raw.createTime),
    sortTime: raw.publishedTime || raw.createTime,
    viewCount: Number(stats?.viewCount || 0),
    likeCount: Number(stats?.likeCount || 0),
    commentCount: resolveDisplayCommentCount(stats),
    liked: Boolean(stats?.liked),
  };
}

export function mapReply(raw: IReplyRaw): PostReply {
  const accountId = raw.accountId;
  return {
    id: String(raw.id),
    accountId,
    nickname: raw.username.trim(),
    avatar: raw.avatar,
    replyToAccountId: raw.replyToAccountId,
    replyToNickname: raw.replyToUsername,
    content: raw.content,
    likeCount: Number(raw.likeCount || 0),
    liked: Boolean(raw.liked),
    createdAt: formatDateTime(raw.createTime),
  };
}

export function mapComment(
  raw: ICommentRaw,
  replies: PostReply[] = [],
): PostComment {
  const accountId = raw.accountId;
  return {
    id: String(raw.id),
    accountId,
    nickname: raw.username.trim(),
    avatar: raw.avatar,
    content: raw.content,
    likeCount: Number(raw.likeCount || 0),
    liked: Boolean(raw.liked),
    replyCount: Math.max(Number(raw.replyCount ?? 0), replies.length),
    createdAt: formatDateTime(raw.createTime),
    replies,
  };
}
