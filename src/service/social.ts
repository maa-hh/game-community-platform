import hyRequest from '@/service/request';
import { ENABLE_MOCK } from '@/service/config';
import {
  MOCK_COMMENTS,
  MOCK_LATEST_POSTS,
  MOCK_POST_DETAILS,
} from '@/mock/posts';
import {
  MOCK_FAN_USERS,
  MOCK_FEEDS,
  MOCK_FOLLOW_USERS,
  MOCK_STATS,
} from '@/mock/profile';
import {
  ARTICLE_STATUS,
  POST_TYPE,
  getArticleDetailApi,
  getLatestArticlesApi,
  getMoreArticlesApi,
  listCategoriesApi,
  saveArticleApi,
} from '@/service/content';
import { getUsersByAccountIdsApi } from '@/service/account';
import type { IDataType, IPageResult, IUserCard } from '@/service/types';
import type {
  LatestPostItem,
  PostComment,
  PostDetailData,
  PostRefCard,
  PostReply,
  PostStats,
} from '@/types/post';
import { applyCurrentUserOwnerAuthor } from '@/utils/profileAuthor';
import {
  authorFrom,
  attachRefStats,
  mapArticleDetail,
  mapArticleToFeedItem,
  mapArticleToLatest,
  mapComment,
  mapReply,
  resolveArticleGameTags,
  type IArticleRaw,
  type IArticleStatsRaw,
  type ICommentRaw,
  type IReplyRaw,
} from '@/utils/mapPost';
import { formatCardTime } from '@/utils/formatTime';
import { mapNumericPostType } from '@/utils/postType';
import type { FeedItemData, ProfileStats } from '@/types/profile';
import type { ContentCardAuthor } from '@/types/content';
import {
  buildRefPostFromArticle,
  wrapActivityFeedItem,
} from '@/utils/profileActivity';
import {
  buildUnavailableRefPost,
  buildRefPostFromArticleRaw,
  isArticlePubliclyVisible,
  resolveUnavailableReason,
} from '@/utils/refPostCard';
import {
  buildGameRepostRefPostForArticle,
  isGameRepostArticle,
  resolveGameRepostAppId,
  sanitizeGameShareContent,
} from '@/utils/gameRepost';
import { fetchGameRepostMetaMap } from '@/utils/fetchGameRepostMeta';
import { hydrateArticlesForGameRepost } from '@/utils/hydrateArticleForGameRepost';
import {
  buildDefaultRepostContent,
  buildDefaultRepostTitle,
  resolveShareContent,
  resolveShareTitle,
} from '@/utils/shareRepost';
import { hasAuthSession, getUserInfo } from '@/utils/storage';
import {
  enqueueCommentLikeAction,
  enqueueFavoriteAction,
  enqueuePostLikeAction,
  enqueueReplyLikeAction,
} from '@/utils/likeActionQueue';

function ok<T>(data: T): IDataType<T> {
  return { code: 200, message: 'success', data };
}

function delay<T>(data: T, ms = 280): Promise<IDataType<T>> {
  return new Promise((resolve) => {
    window.setTimeout(() => resolve(ok(data)), ms);
  });
}

/** 内存态：mock 下点赞/评论等写操作 */
const detailStore: Record<string, PostDetailData> =
  structuredClone(MOCK_POST_DETAILS);
const commentStore: Record<string, PostComment[]> =
  structuredClone(MOCK_COMMENTS);
type MockPostInteraction = Pick<PostStats, 'liked' | 'favorited'>;
const mockInteractionStore: Record<
  string,
  Record<string, MockPostInteraction>
> = {};
let commentSeq = 1000;
let replySeq = 2000;
let repostSeq = 9000;

function getMockInteractionScope(): string {
  return String(getUserInfo()?.accountId ?? 'anonymous');
}

function getMockPostInteraction(articleId: string): MockPostInteraction {
  const scope = getMockInteractionScope();
  const stored = mockInteractionStore[scope]?.[articleId];
  const base = detailStore[articleId]?.stats;
  return {
    liked: stored?.liked ?? base?.liked ?? false,
    favorited: stored?.favorited ?? base?.favorited ?? false,
  };
}

function setMockPostInteraction(
  articleId: string,
  patch: Partial<MockPostInteraction>,
): MockPostInteraction {
  const scope = getMockInteractionScope();
  const scopeStore = mockInteractionStore[scope] ?? {};
  const next = {
    ...getMockPostInteraction(articleId),
    ...patch,
  };
  scopeStore[articleId] = next;
  mockInteractionStore[scope] = scopeStore;
  return next;
}

async function fetchStats(articleId: string) {
  const res = await hyRequest.get<IDataType<IArticleStatsRaw>>({
    url: `/social/article/count/${articleId}`,
  });
  return normalizeArticleStats(res.data, articleId);
}

function normalizeArticleStats(
  raw: IArticleStatsRaw | null | undefined,
  articleId: string,
): IArticleStatsRaw {
  return {
    articleId: raw?.articleId ?? articleId,
    publicId: raw?.publicId,
    likeCount: Number(raw?.likeCount ?? 0),
    commentCount: Number(raw?.commentCount ?? 0),
    replyCount: Number(raw?.replyCount ?? 0),
    favoriteCount: Number(raw?.favoriteCount ?? 0),
    shareCount: Number(raw?.shareCount ?? 0),
    viewCount: Number(raw?.viewCount ?? 0),
    liked: Boolean(raw?.liked),
    favorited: Boolean(raw?.favorited),
  };
}

async function fetchPostLikeState(
  articleId: string,
): Promise<{ liked: boolean; likeCount: number }> {
  const [stats, checkRes] = await Promise.all([
    fetchStats(articleId),
    hyRequest.get<IDataType<boolean>>({
      url: `/social/like/article/check/${articleId}`,
    }),
  ]);
  return {
    liked: Boolean(checkRes.data),
    likeCount: Number(stats.likeCount ?? 0),
  };
}

async function readArticleLikeCheck(articleId: string): Promise<boolean> {
  const res = await hyRequest.get<IDataType<boolean>>({
    url: `/social/like/article/check/${articleId}`,
  });
  return Boolean(res.data);
}

async function readArticleFavoriteCheck(articleId: string): Promise<boolean> {
  const res = await hyRequest.get<IDataType<boolean>>({
    url: `/social/favorite/article/check/${articleId}`,
  });
  return Boolean(res.data);
}

async function mutateArticleLike(
  articleId: string,
  targetLiked: boolean,
): Promise<void> {
  try {
    if (targetLiked) {
      await hyRequest.post({ url: `/social/like/article/${articleId}` });
    } else {
      await hyRequest.delete({ url: `/social/like/article/${articleId}` });
    }
  } catch (err) {
    const actual = await readArticleLikeCheck(articleId).catch(() => null);
    if (actual === targetLiked) return;
    throw err;
  }
}

async function fetchStatsBatch(articleIds: string[]) {
  const uniqueIds = Array.from(new Set(articleIds.filter(Boolean)));
  if (uniqueIds.length === 0) return [] as IArticleStatsRaw[];
  const params = new URLSearchParams();
  uniqueIds.forEach((id) => params.append('articleIds', id));
  const res = await hyRequest.get<IDataType<IArticleStatsRaw[]>>({
    url: `/social/article/counts?${params.toString()}`,
  });
  return (res.data || []).map((item) =>
    normalizeArticleStats(item, item.publicId || ''),
  );
}

async function readCommentLikeCheck(
  commentId: string | number,
): Promise<boolean> {
  const res = await hyRequest.get<IDataType<boolean>>({
    url: `/social/like/comment/check/${commentId}`,
  });
  return Boolean(res.data);
}

async function readReplyLikeCheck(replyId: string | number): Promise<boolean> {
  const res = await hyRequest.get<IDataType<boolean>>({
    url: `/social/like/reply/check/${replyId}`,
  });
  return Boolean(res.data);
}

async function mutateCommentLike(
  commentId: string | number,
  targetLiked: boolean,
): Promise<void> {
  try {
    if (targetLiked) {
      await hyRequest.post({ url: `/social/like/comment/${commentId}` });
    } else {
      await hyRequest.delete({ url: `/social/like/comment/${commentId}` });
    }
  } catch (err) {
    const actual = await readCommentLikeCheck(commentId).catch(() => null);
    if (actual === targetLiked) return;
    throw err;
  }
}

async function mutateReplyLike(
  replyId: string | number,
  targetLiked: boolean,
): Promise<void> {
  try {
    if (targetLiked) {
      await hyRequest.post({ url: `/social/like/reply/${replyId}` });
    } else {
      await hyRequest.delete({ url: `/social/like/reply/${replyId}` });
    }
  } catch (err) {
    const actual = await readReplyLikeCheck(replyId).catch(() => null);
    if (actual === targetLiked) return;
    throw err;
  }
}

async function togglePostLikeApiImpl(
  articleId: string,
  targetLiked: boolean,
): Promise<IDataType<{ likeCount: number; liked: boolean }>> {
  let state: { liked: boolean; likeCount: number };
  try {
    state = await fetchPostLikeState(articleId);
  } catch (err) {
    throw Object.assign(new Error('无法获取点赞状态，请稍后重试'), {
      cause: err,
    });
  }

  if (state.liked === targetLiked) {
    return ok({ liked: state.liked, likeCount: state.likeCount });
  }

  await mutateArticleLike(articleId, targetLiked);

  try {
    state = await fetchPostLikeState(articleId);
  } catch (err) {
    throw Object.assign(new Error('点赞请求失败，请稍后重试'), { cause: err });
  }

  if (state.liked !== targetLiked) {
    throw new Error('点赞状态未同步，请刷新后重试');
  }

  return ok({ liked: state.liked, likeCount: state.likeCount });
}

async function resolveUsersByAccountIds(accountIds: number[]) {
  const unique = Array.from(new Set(accountIds.filter((id) => id > 0)));
  if (unique.length === 0) {
    return new Map<number, ReturnType<typeof authorFrom>>();
  }
  try {
    const res = await getUsersByAccountIdsApi(unique);
    const map = new Map<number, ReturnType<typeof authorFrom>>();
    (res.data || []).forEach((u) => {
      map.set(u.accountId, authorFrom(u.accountId, u.username, u.avatar));
    });
    return map;
  } catch {
    return new Map<number, ReturnType<typeof authorFrom>>();
  }
}

async function resolveAuthorsFromArticles(articles: IArticleRaw[]) {
  const accountIds = articles
    .map((article) => article.authorAccountId)
    .filter((id): id is number => typeof id === 'number' && id > 0);
  return resolveUsersByAccountIds(accountIds);
}

function resolveArticleAuthor(
  raw: IArticleRaw,
  authors: Map<number, ReturnType<typeof authorFrom>>,
) {
  if (raw.authorAccountId && authors.has(raw.authorAccountId)) {
    return authors.get(raw.authorAccountId)!;
  }
  if (raw.authorAccountId) {
    return authorFrom(raw.authorAccountId, raw.username, raw.avatar);
  }
  return authorFrom(0, raw.username, raw.avatar);
}

async function loadCategoryName(categoryId?: number) {
  if (!categoryId) return undefined;
  try {
    const res = await listCategoriesApi(1, 100);
    return res.data?.find((c) => c.id === categoryId)?.name;
  } catch {
    return undefined;
  }
}

async function loadCategoryMap() {
  try {
    const res = await listCategoriesApi(1, 100);
    return new Map((res.data || []).map((category) => [category.id, category]));
  } catch {
    return new Map<number, import('@/service/content').ICategory>();
  }
}

/** 批量解析转发帖引用的原帖卡片数据 */
export async function resolveRefPostMap(
  articles: IArticleRaw[],
): Promise<Map<string, PostRefCard>> {
  const refIds = Array.from(
    new Set(
      articles
        .filter(
          (a) => mapNumericPostType(a.postType) === 'repost' && a.refArticleId,
        )
        .map((a) => a.refArticleId!),
    ),
  );
  if (refIds.length === 0) return new Map();

  const [refStatsList, entries] = await Promise.all([
    fetchStatsBatch(refIds),
    Promise.all(
      refIds.map(async (refId) => {
        try {
          const res = await getArticleDetailApi(refId);
          const raw = res.data;
          if (!raw) {
            return [refId, buildUnavailableRefPost(refId, 'deleted')] as const;
          }
          if (!isArticlePubliclyVisible(raw.status)) {
            return [
              refId,
              buildUnavailableRefPost(
                refId,
                resolveUnavailableReason(raw.status),
                raw.title,
              ),
            ] as const;
          }

          const authors = await resolveAuthorsFromArticles([
            {
              id: raw.id,
              publicId: raw.publicId,
              title: raw.title,
              authorAccountId: raw.authorAccountId,
            },
          ]);
          const author =
            (raw.authorAccountId && authors.get(raw.authorAccountId)) ||
            authorFrom(raw.authorAccountId, raw.username, raw.avatar);
          const refPost = buildRefPostFromArticleRaw(
            {
              id: raw.id,
              publicId: raw.publicId,
              authorAccountId: raw.authorAccountId,
              title: raw.title,
              summary: raw.summary,
              coverUrl: raw.coverUrl,
              videoUrl: raw.videoUrl,
              postType: raw.postType,
              status: raw.status,
              imageUrls: raw.imageUrls,
            },
            author,
          );
          return [refId, refPost] as const;
        } catch {
          return [
            refId,
            buildUnavailableRefPost(refId, 'unavailable'),
          ] as const;
        }
      }),
    ),
  ]);
  const refStatsMap = new Map(
    refStatsList
      .filter((s): s is IArticleStatsRaw & { publicId: string } =>
        Boolean(s.publicId),
      )
      .map((s) => [s.publicId, s]),
  );

  const map = new Map<string, PostRefCard>();
  entries.forEach((entry) => {
    const [refId, refPost] = entry;
    map.set(refId, attachRefStats(refPost, refStatsMap.get(refId)));
  });
  return map;
}

function currentMeAuthor(): ContentCardAuthor {
  const user = getUserInfo();
  return {
    accountId: user?.accountId ?? 0,
    nickname: user?.username || '我',
    avatar: user?.avatar,
  };
}

async function resolveArticlesByIds(
  ids: string[],
): Promise<Map<string, IArticleRaw>> {
  const unique = Array.from(new Set(ids.filter(Boolean)));
  if (unique.length === 0) return new Map();
  const entries = await Promise.all(
    unique.map(async (id) => {
      try {
        const res = await getArticleDetailApi(id);
        return res.data ? ([id, res.data] as const) : null;
      } catch {
        return null;
      }
    }),
  );
  const map = new Map<string, IArticleRaw>();
  entries.forEach((entry) => {
    if (entry) map.set(entry[0], entry[1]);
  });
  return map;
}

async function mapArticlesPageToFeedItems(
  articles: IArticleRaw[],
  options?: {
    liked?: boolean;
    createdAtOf?: (raw: IArticleRaw) => string | undefined;
    sortTimeOf?: (raw: IArticleRaw) => string | undefined;
  },
): Promise<FeedItemData[]> {
  if (articles.length === 0) return [];
  const [statsList, authors, refPostMap] = await Promise.all([
    fetchStatsBatch(articles.map((a) => a.publicId)),
    resolveAuthorsFromArticles(articles),
    resolveRefPostMap(articles),
  ]);
  const statsMap = new Map(
    statsList
      .filter((s): s is IArticleStatsRaw & { publicId: string } =>
        Boolean(s.publicId),
      )
      .map((s) => [s.publicId, s]),
  );
  return articles.map((raw) => {
    const postType = mapNumericPostType(raw.postType);
    const feed = mapArticleToFeedItem(
      raw,
      resolveArticleAuthor(raw, authors),
      statsMap.get(raw.publicId),
      postType === 'repost' && raw.refArticleId
        ? refPostMap.get(raw.refArticleId)
        : undefined,
    );
    const createdAtOverride = options?.createdAtOf?.(raw);
    const sortTimeOverride = options?.sortTimeOf?.(raw);
    return {
      ...feed,
      liked: options?.liked ?? feed.liked,
      createdAt: createdAtOverride
        ? formatCardTime(createdAtOverride)
        : feed.createdAt,
      sortTime: sortTimeOverride ?? feed.sortTime,
    };
  });
}

/** 为个人页「我的帖子」等列表批量补全互动统计（含嵌套 refPost 点赞态） */
export async function enrichFeedItemsWithStats(
  items: FeedItemData[],
): Promise<FeedItemData[]> {
  const ids = items.map((item) => String(item.id)).filter(Boolean);
  const refIds = items
    .map((item) =>
      item.refPost && !item.refPost.unavailable ? String(item.refPost.id) : '',
    )
    .filter(Boolean);
  const statIds = Array.from(new Set([...ids, ...refIds]));
  if (statIds.length === 0) return items;

  let statsList: IArticleStatsRaw[] = [];
  try {
    statsList = await fetchStatsBatch(statIds);
  } catch {
    return items.map((item) => ({
      ...item,
      viewCount: item.viewCount ?? 0,
      likeCount: item.likeCount ?? 0,
      commentCount: item.commentCount ?? 0,
      shareCount: item.shareCount ?? 0,
      liked: Boolean(item.liked),
      refPost: item.refPost,
    }));
  }

  const statsMap = new Map(
    statsList
      .filter((s): s is IArticleStatsRaw & { publicId: string } =>
        Boolean(s.publicId),
      )
      .map((s) => [s.publicId, s]),
  );
  return items.map((item) => {
    const stats = statsMap.get(String(item.id));
    const nextRefPost =
      item.refPost && !item.refPost.unavailable
        ? attachRefStats(item.refPost, statsMap.get(String(item.refPost.id)))
        : item.refPost;

    if (!stats) {
      return {
        ...item,
        viewCount: item.viewCount ?? 0,
        likeCount: item.likeCount ?? 0,
        commentCount: item.commentCount ?? 0,
        shareCount: item.shareCount ?? 0,
        liked: Boolean(item.liked),
        refPost: nextRefPost,
      };
    }
    return {
      ...item,
      viewCount: Number(stats.viewCount ?? item.viewCount ?? 0),
      likeCount: Number(stats.likeCount ?? 0),
      commentCount: Number(stats.commentCount ?? item.commentCount ?? 0),
      shareCount: Number(stats.shareCount ?? item.shareCount ?? 0),
      liked: Boolean(stats.liked),
      refPost: nextRefPost,
    };
  });
}

export async function fetchLatestPostsPageApi(
  options: { lastId?: string; size?: number } = {},
): Promise<IDataType<LatestPostItem[]>> {
  const size = options.size ?? 20;
  if (ENABLE_MOCK) {
    let list = MOCK_LATEST_POSTS.map((item) => {
      const d = detailStore[item.id];
      if (!d) return item;
      return {
        ...item,
        refPost: d.refPost ?? item.refPost,
        videoUrl: d.videoUrl ?? item.videoUrl,
        likeCount: d.stats.likeCount,
        commentCount: d.stats.commentCount,
        viewCount: d.stats.viewCount,
        favoriteCount: d.stats.favoriteCount,
        liked: getMockPostInteraction(item.id).liked,
        favorited: getMockPostInteraction(item.id).favorited,
      };
    });
    if (options.lastId != null) {
      const idx = list.findIndex((item) => String(item.id) === options.lastId);
      list = idx >= 0 ? list.slice(idx + 1, idx + 1 + size) : [];
    } else {
      list = list.slice(0, size);
    }
    return delay(list);
  }

  const listRes =
    options.lastId != null
      ? await getMoreArticlesApi(options.lastId, undefined, size)
      : await getLatestArticlesApi(undefined, size);
  const articles = (listRes.data || []) as IArticleRaw[];
  return ok(await mapArticlesToLatestPosts(articles));
}

/** 将文章列表映射为首页同款信息流卡片数据（补全作者与互动统计） */
export async function mapArticlesToLatestPosts(
  articles: IArticleRaw[],
): Promise<LatestPostItem[]> {
  if (articles.length === 0) return [];
  const hydrated = await hydrateArticlesForGameRepost(articles);
  const [statsList, authors, refPostMap, categoryMap] = await Promise.all([
    fetchStatsBatch(hydrated.map((a) => a.publicId)),
    resolveAuthorsFromArticles(hydrated),
    resolveRefPostMap(hydrated),
    loadCategoryMap(),
  ]);
  const statsMap = new Map(
    statsList
      .filter((s): s is IArticleStatsRaw & { publicId: string } =>
        Boolean(s.publicId),
      )
      .map((s) => [s.publicId, s]),
  );
  const gameRepostAppIds = hydrated
    .filter((article) => isGameRepostArticle(article))
    .map((article) => resolveGameRepostAppId(article))
    .filter((appId): appId is number => appId != null && appId > 0);
  const gameRepostMetaMap = await fetchGameRepostMetaMap(gameRepostAppIds);
  const items = hydrated.map((a) => {
    const appId = resolveGameRepostAppId(a);
    const gameRepostRef = isGameRepostArticle(a)
      ? buildGameRepostRefPostForArticle(
          a,
          appId ? gameRepostMetaMap[appId] : undefined,
        )
      : undefined;
    const refPost = gameRepostRef
      ? gameRepostRef
      : a.refArticleId
        ? refPostMap.get(a.refArticleId)
        : undefined;
    return mapArticleToLatest(
      a,
      resolveArticleAuthor(a, authors),
      statsMap.get(a.publicId),
      refPost,
      categoryMap,
    );
  });
  return applyCurrentUserOwnerAuthor(items);
}

export async function fetchLatestPostsApi(): Promise<
  IDataType<LatestPostItem[]>
> {
  return fetchLatestPostsPageApi({ size: 20 });
}

/** 转发引用补全视频地址与互动统计 */
async function enrichRepostRef(
  detail: PostDetailData,
  refArticleId?: string,
): Promise<PostDetailData> {
  const ref = detail.refPost;
  if (detail.postType !== 'repost' || !ref || !refArticleId) {
    return detail;
  }

  let refPost = ref;

  if (ref.unavailable) {
    return detail;
  }

  if (ref.postType === 'video' && !ref.videoUrl) {
    try {
      const refRes = await getArticleDetailApi(refArticleId);
      const refRaw = refRes.data;
      if (refRaw?.videoUrl || refRaw?.coverUrl) {
        refPost = {
          ...refPost,
          coverUrl: ref.coverUrl || refRaw.coverUrl,
          videoUrl: refRaw.videoUrl || ref.videoUrl,
        };
      }
    } catch {
      /* ignore */
    }
  }

  try {
    const refStats = await fetchStats(refArticleId);
    refPost = attachRefStats(refPost, refStats);
  } catch {
    /* ignore */
  }

  return { ...detail, refPost };
}

export async function fetchPostDetailApi(
  id: string,
): Promise<IDataType<PostDetailData | null>> {
  if (!id?.trim() || id.startsWith('game-')) {
    return ok(null);
  }

  if (ENABLE_MOCK) {
    const detail = detailStore[id] || null;
    if (!detail) return delay(null);
    detail.stats.viewCount += 1;
    const result = structuredClone(detail);
    const interaction = getMockPostInteraction(id);
    result.stats.liked = interaction.liked;
    result.stats.favorited = interaction.favorited;
    return delay(result);
  }

  try {
    const detailRes = await getArticleDetailApi(id);
    const raw = detailRes.data;
    if (!raw) return ok(null);

    const [stats, categoryMap, followedAuthor] = await Promise.all([
      fetchStats(id).catch(() => null),
      loadCategoryMap(),
      hasAuthSession() && raw.authorAccountId
        ? checkFollowByAccountApi(raw.authorAccountId)
            .then((r) => Boolean(r.data?.followed))
            .catch(() => false)
        : Promise.resolve(false),
    ]);

    const categoryName = raw.categoryId
      ? categoryMap.get(raw.categoryId)?.name
      : undefined;

    // 登录用户记一次浏览（失败忽略）
    if (hasAuthSession()) {
      void hyRequest
        .get({ url: `/social/article/${id}` })
        .catch(() => undefined);
    }

    let detail = mapArticleDetail(raw, stats, {
      categoryName,
      followedAuthor,
      categoryMap,
    });

    try {
      if (detail.postType === 'repost' && raw.refArticleId) {
        const refMap = await resolveRefPostMap([
          {
            id: raw.id,
            publicId: raw.publicId,
            authorAccountId: raw.authorAccountId,
            title: raw.title,
            postType: raw.postType,
            refArticleId: raw.refArticleId,
          },
        ]);
        detail = {
          ...detail,
          refPost:
            refMap.get(raw.refArticleId) ??
            buildUnavailableRefPost(raw.refArticleId, 'unavailable'),
        };
      } else if (isGameRepostArticle(raw)) {
        const appId = resolveGameRepostAppId(raw);
        const metaMap = appId ? await fetchGameRepostMetaMap([appId]) : {};
        const gameRef = buildGameRepostRefPostForArticle(
          raw,
          appId ? metaMap[appId] : undefined,
        );
        if (gameRef) {
          detail = {
            ...detail,
            gameTags: resolveArticleGameTags(raw),
            refPost: gameRef,
          };
        }
      }

      detail = await enrichRepostRef(detail, raw.refArticleId);
    } catch {
      // 主帖已经加载成功，引用卡片失败时保留主帖，不误报“帖子不存在”。
    }

    return ok(detail);
  } catch {
    return ok(null);
  }
}

async function patchCommentUsers(
  comments: PostComment[],
): Promise<PostComment[]> {
  const accountIds = new Set<number>();
  comments.forEach((comment) => {
    if (comment.accountId) accountIds.add(comment.accountId);
    comment.replies.forEach((reply) => {
      if (reply.accountId) accountIds.add(reply.accountId);
      if (reply.replyToAccountId) accountIds.add(reply.replyToAccountId);
    });
  });
  if (accountIds.size === 0) return comments;

  const users = await resolveUsersByAccountIds(Array.from(accountIds));
  return comments.map((comment) => {
    const author = comment.accountId ? users.get(comment.accountId) : undefined;
    return {
      ...comment,
      accountId: author?.accountId ?? comment.accountId,
      nickname: author?.nickname || comment.nickname,
      avatar: author?.avatar || comment.avatar,
      replies: comment.replies.map((reply) => {
        const replyAuthor = reply.accountId
          ? users.get(reply.accountId)
          : undefined;
        const replyToAuthor = reply.replyToAccountId
          ? users.get(reply.replyToAccountId)
          : undefined;
        return {
          ...reply,
          accountId: replyAuthor?.accountId ?? reply.accountId,
          nickname: replyAuthor?.nickname || reply.nickname,
          avatar: replyAuthor?.avatar || reply.avatar,
          replyToAccountId: replyToAuthor?.accountId ?? reply.replyToAccountId,
          replyToNickname: replyToAuthor?.nickname || reply.replyToNickname,
        };
      }),
    };
  });
}

async function patchReplyUsers(replies: PostReply[]): Promise<PostReply[]> {
  const accountIds = new Set<number>();
  replies.forEach((reply) => {
    if (reply.accountId) accountIds.add(reply.accountId);
    if (reply.replyToAccountId) accountIds.add(reply.replyToAccountId);
  });
  if (accountIds.size === 0) return replies;

  const users = await resolveUsersByAccountIds(Array.from(accountIds));
  return replies.map((reply) => {
    const author = reply.accountId ? users.get(reply.accountId) : undefined;
    const replyToAuthor = reply.replyToAccountId
      ? users.get(reply.replyToAccountId)
      : undefined;
    return {
      ...reply,
      accountId: author?.accountId ?? reply.accountId,
      nickname: author?.nickname || reply.nickname,
      avatar: author?.avatar || reply.avatar,
      replyToAccountId: replyToAuthor?.accountId ?? reply.replyToAccountId,
      replyToNickname: replyToAuthor?.nickname || reply.replyToNickname,
    };
  });
}

export async function fetchPostCommentsPageApi(
  articleId: string,
  page = 1,
  size = 20,
  options: {
    withReplies?: boolean;
    replyPageSize?: number;
  } = {},
): Promise<IPageResult<PostComment>> {
  const { withReplies = false, replyPageSize = 20 } = options;
  if (ENABLE_MOCK) {
    const all = structuredClone(commentStore[articleId] || []);
    const start = (page - 1) * size;
    const slice = all.slice(start, start + size).map((comment: PostComment) => {
      if (comment.replyCount <= 0) {
        return { ...comment, replies: [] };
      }
      return {
        ...comment,
        replyPage: 1,
        replyPageSize,
        replies: comment.replies.slice(0, replyPageSize),
      };
    });
    return {
      code: 200,
      message: 'success',
      data: slice,
      page,
      size,
      total: all.length,
    };
  }

  const pageRes = await hyRequest.get<IPageResult<ICommentRaw>>({
    url: `/social/comment/list/${articleId}`,
    params: { page, size },
  });
  const comments = pageRes.data || [];
  const initialReplyPageSize = withReplies
    ? Math.min(100, Math.max(replyPageSize, 1))
    : Math.max(replyPageSize, 1);
  const mapped = await Promise.all(
    comments.map(async (c) => {
      // 某些旧数据的 replyCount 尚未回填，但回复接口仍有真实数据；
      // 只要调用方要求预览就请求一次，避免整帖回复全部消失。
      try {
        const replyRes = await fetchPostRepliesPageApi(
          String(c.id),
          1,
          initialReplyPageSize,
        );
        return mapComment(
          c,
          replyRes.data || [],
          Number(replyRes.page ?? 1),
          initialReplyPageSize,
        );
      } catch {
        // 单条历史脏数据不能阻断整页评论；用户仍可通过“展开”重试。
        return mapComment(c, []);
      }
    }),
  );
  const data = await patchCommentUsers(mapped);
  return {
    ...pageRes,
    data,
  };
}

export async function fetchPostRepliesPageApi(
  commentId: string,
  page = 1,
  size = 20,
): Promise<IPageResult<PostReply>> {
  if (ENABLE_MOCK) {
    const comment = Object.values(commentStore)
      .flat()
      .find((item) => item.id === commentId);
    const replies = comment?.replies || [];
    return {
      code: 200,
      message: 'success',
      data: replies.slice((page - 1) * size, page * size),
      page,
      size,
      total: replies.length,
    };
  }
  const pageRes = await hyRequest.get<IPageResult<IReplyRaw>>({
    url: `/social/reply/list/${commentId}`,
    params: { page, size },
  });
  const data = await patchReplyUsers((pageRes.data || []).map(mapReply));
  return {
    ...pageRes,
    data,
  };
}

export async function fetchPostCommentsApi(
  articleId: string,
): Promise<IDataType<PostComment[]>> {
  const pageRes = await fetchPostCommentsPageApi(articleId, 1, 50, {
    withReplies: true,
  });
  return ok(pageRes.data || []);
}

/** 通知定位用的单条评论详情；不改变正常评论分页。 */
export async function fetchPostCommentDetailApi(
  articleId: string,
  commentId: string,
): Promise<PostComment | null> {
  if (ENABLE_MOCK) {
    const comment = (commentStore[articleId] || []).find(
      (item) => item.id === commentId,
    );
    return comment ? structuredClone(comment) : null;
  }
  const res = await hyRequest.get<IDataType<ICommentRaw>>({
    url: `/social/comment/${commentId}`,
  });
  return res.data ? mapComment(res.data, []) : null;
}

/** 通知定位用的单条回复详情；回复仍由 CommentItem 负责交互。 */
export async function fetchPostReplyDetailApi(
  replyId: string,
): Promise<PostReply | null> {
  if (ENABLE_MOCK) {
    for (const comments of Object.values(commentStore)) {
      const reply = comments
        .flatMap((comment) => comment.replies)
        .find((item) => item.id === replyId);
      if (reply) return structuredClone(reply);
    }
    return null;
  }
  const res = await hyRequest.get<IDataType<IReplyRaw>>({
    url: `/social/reply/${replyId}`,
  });
  return res.data ? mapReply(res.data) : null;
}

export async function togglePostLikeApi(
  articleId: string,
  liked: boolean,
): Promise<IDataType<{ likeCount: number; liked: boolean }>> {
  if (ENABLE_MOCK) {
    let d = detailStore[articleId];
    if (!d) {
      try {
        const detailRes = await getArticleDetailApi(articleId);
        const stats = await fetchStats(articleId).catch(() => null);
        const categoryName = await loadCategoryName(detailRes.data.categoryId);
        d = mapArticleDetail(detailRes.data, stats, { categoryName });
        detailStore[articleId] = d;
      } catch {
        d = {
          id: articleId,
          postType: 'image_text',
          title: '',
          content: '',
          author: {
            accountId: getUserInfo()?.accountId ?? 0,
            nickname: getUserInfo()?.username || '我',
            avatar: getUserInfo()?.avatar,
          },
          createdAt: '',
          stats: {
            viewCount: 0,
            likeCount: 0,
            commentCount: 0,
            favoriteCount: 0,
            shareCount: 0,
            liked: false,
            favorited: false,
          },
        };
        detailStore[articleId] = d;
      }
    }
    const current = getMockPostInteraction(articleId);
    const wasLiked = current.liked;
    const interaction = setMockPostInteraction(articleId, { liked });
    if (wasLiked !== liked) {
      d.stats.likeCount = Math.max(0, d.stats.likeCount + (liked ? 1 : -1));
    }
    return delay({ likeCount: d.stats.likeCount, liked: interaction.liked });
  }

  return enqueuePostLikeAction(articleId, () =>
    togglePostLikeApiImpl(articleId, liked),
  );
}

export async function toggleFavoriteApi(
  articleId: string,
  favorited: boolean,
): Promise<IDataType<{ favoriteCount: number; favorited: boolean }>> {
  return enqueueFavoriteAction(articleId, async () => {
    if (ENABLE_MOCK) {
      const d = detailStore[articleId];
      if (!d) throw new Error('帖子不存在');
      const current = getMockPostInteraction(articleId);
      const wasFavorited = current.favorited;
      const interaction = setMockPostInteraction(articleId, { favorited });
      if (wasFavorited !== favorited) {
        d.stats.favoriteCount = Math.max(
          0,
          d.stats.favoriteCount + (favorited ? 1 : -1),
        );
      }
      return delay({
        favoriteCount: d.stats.favoriteCount,
        favorited: interaction.favorited,
      });
    }

    if (favorited) {
      await hyRequest.post({ url: `/social/favorite/article/${articleId}` });
    } else {
      await hyRequest.delete({ url: `/social/favorite/article/${articleId}` });
    }
    const [stats, actualFavorited] = await Promise.all([
      fetchStats(articleId),
      readArticleFavoriteCheck(articleId),
    ]);
    if (actualFavorited !== favorited) {
      throw new Error('收藏状态未同步，请刷新后重试');
    }
    return ok({
      favoriteCount: Number(stats.favoriteCount || 0),
      favorited: actualFavorited,
    });
  });
}

export async function recordShareApi(
  articleId: string,
  channel: 'link' | 'repost' | 'external' = 'link',
): Promise<IDataType<{ shareCount: number }>> {
  if (ENABLE_MOCK) {
    const d = detailStore[articleId];
    if (!d) throw new Error('帖子不存在');
    d.stats.shareCount += 1;
    return delay({ shareCount: d.stats.shareCount });
  }

  await hyRequest.post({
    url: `/social/share/article/${articleId}`,
    data: { channel },
  });
  const stats = await fetchStats(articleId);
  return ok({ shareCount: Number(stats.shareCount || 0) });
}

export async function toggleFollowByAccountApi(
  accountId: number,
  followed: boolean,
): Promise<IDataType<{ followed: boolean }>> {
  if (ENABLE_MOCK) {
    return delay({ followed });
  }
  if (followed) {
    await hyRequest.post({ url: `/social/follow/by-account/${accountId}` });
  } else {
    await hyRequest.delete({ url: `/social/follow/by-account/${accountId}` });
  }
  return ok({ followed });
}

export async function toggleBlockByAccountApi(
  accountId: number,
  blocked: boolean,
): Promise<IDataType<{ blocked: boolean }>> {
  if (ENABLE_MOCK) {
    return delay({ blocked });
  }
  if (blocked) {
    await hyRequest.post({
      url: `/social/follow/black/by-account/${accountId}`,
    });
  } else {
    await hyRequest.delete({
      url: `/social/follow/black/by-account/${accountId}`,
    });
  }
  return ok({ blocked });
}

export async function checkBlockByAccountApi(
  accountId: number,
): Promise<boolean> {
  if (ENABLE_MOCK) return false;
  try {
    const res = await hyRequest.get<IDataType<boolean>>({
      url: `/social/follow/black/check/by-account/${accountId}`,
    });
    return Boolean(res.data);
  } catch {
    return false;
  }
}

export interface IFetchFollowFeedParams {
  before?: string;
  size?: number;
  postType?: number;
  includeSelf?: boolean;
}

/** 关注流：关注的人 + 自己的已发布帖（可筛 postType） */
export async function fetchFollowFeedApi(
  params: IFetchFollowFeedParams = {},
): Promise<IDataType<LatestPostItem[]>> {
  const { before, size = 20, postType, includeSelf = true } = params;

  if (ENABLE_MOCK) {
    let list = MOCK_LATEST_POSTS.map((item) => {
      const d = detailStore[item.id];
      if (!d) return item;
      return {
        ...item,
        refPost: d.refPost ?? item.refPost,
        videoUrl: d.videoUrl ?? item.videoUrl,
        likeCount: d.stats.likeCount,
        commentCount: d.stats.commentCount,
        viewCount: d.stats.viewCount,
        favoriteCount: d.stats.favoriteCount,
        liked: getMockPostInteraction(item.id).liked,
        favorited: getMockPostInteraction(item.id).favorited,
      };
    });
    if (postType != null) {
      const type = mapNumericPostType(postType);
      list = list.filter((item) => item.postType === type);
    }
    if (before) {
      const idx = list.findIndex((item) => item.sortTime === before);
      list = idx >= 0 ? list.slice(idx + 1, idx + 1 + size) : [];
    } else {
      list = list.slice(0, size);
    }
    return delay(list);
  }

  const pageRes = await hyRequest.get<IPageResult<IArticleRaw>>({
    url: '/social/feed',
    params: {
      before,
      size,
      postType,
      includeSelf,
    },
  });
  return ok(await mapArticlesToLatestPosts(pageRes.data || []));
}

export async function createCommentApi(
  articleId: string,
  content: string,
  user: { accountId: number; nickname: string; avatar?: string },
): Promise<IDataType<PostComment>> {
  if (ENABLE_MOCK) {
    const list = commentStore[articleId] || (commentStore[articleId] = []);
    const item: PostComment = {
      id: `c${++commentSeq}`,
      accountId: user.accountId,
      nickname: user.nickname,
      avatar: user.avatar,
      content,
      likeCount: 0,
      liked: false,
      replyCount: 0,
      createdAt: '刚刚',
      replies: [],
    };
    list.unshift(item);
    const d = detailStore[articleId];
    if (d) d.stats.commentCount += 1;
    return delay(structuredClone(item));
  }

  const res = await hyRequest.post<IDataType<number>>({
    url: '/social/comment',
    data: { articleId, content },
  });
  const detail = await hyRequest.get<IDataType<ICommentRaw>>({
    url: `/social/comment/${res.data}`,
  });
  return ok(mapComment(detail.data, []));
}

export async function createReplyApi(
  articleId: string,
  commentId: string,
  content: string,
  replyTo: { accountId: number; nickname: string },
  user: { accountId: number; nickname: string; avatar?: string },
  afterReplyId?: string | null,
): Promise<IDataType<PostReply>> {
  if (!Number.isInteger(replyTo.accountId) || replyTo.accountId <= 0) {
    throw new Error('回复对象信息缺失，请刷新消息后重试');
  }

  if (ENABLE_MOCK) {
    const list = commentStore[articleId] || [];
    const comment = list.find((c) => c.id === commentId);
    if (!comment) throw new Error('评论不存在');
    const reply: PostReply = {
      id: `r${++replySeq}`,
      accountId: user.accountId,
      nickname: user.nickname,
      avatar: user.avatar,
      replyToAccountId: replyTo.accountId,
      replyToNickname: replyTo.nickname,
      content,
      likeCount: 0,
      liked: false,
      createdAt: '刚刚',
    };
    if (afterReplyId) {
      const idx = comment.replies.findIndex((r) => r.id === afterReplyId);
      if (idx >= 0) comment.replies.splice(idx + 1, 0, reply);
      else comment.replies.unshift(reply);
    } else {
      comment.replies.unshift(reply);
    }
    comment.replyCount += 1;
    const d = detailStore[articleId];
    if (d) d.stats.commentCount += 1;
    return delay(structuredClone(reply));
  }

  const res = await hyRequest.post<IDataType<number>>({
    url: '/social/reply',
    data: {
      commentId: Number(commentId),
      replyToAccountId: replyTo.accountId,
      content,
    },
  });
  const detail = await hyRequest.get<IDataType<IReplyRaw>>({
    url: `/social/reply/${res.data}`,
  });
  return ok(mapReply(detail.data));
}

export async function deleteCommentApi(
  articleId: string,
  commentId: string,
): Promise<IDataType<null>> {
  if (ENABLE_MOCK) {
    const list = commentStore[articleId] || [];
    const idx = list.findIndex((c) => c.id === commentId);
    if (idx >= 0) {
      const removed = list[idx];
      list.splice(idx, 1);
      const d = detailStore[articleId];
      if (d) {
        d.stats.commentCount = Math.max(
          0,
          d.stats.commentCount - 1 - removed.replies.length,
        );
      }
    }
    return delay(null);
  }
  await hyRequest.delete({ url: `/social/comment/${commentId}` });
  return ok(null);
}

export async function deleteReplyApi(
  _articleId: string,
  _commentId: string,
  replyId: string,
): Promise<IDataType<null>> {
  if (ENABLE_MOCK) {
    const comment = (commentStore[_articleId] || []).find(
      (c) => c.id === _commentId,
    );
    if (comment) {
      comment.replies = comment.replies.filter((r) => r.id !== replyId);
      comment.replyCount = comment.replies.length;
      const d = detailStore[_articleId];
      if (d) d.stats.commentCount = Math.max(0, d.stats.commentCount - 1);
    }
    return delay(null);
  }
  await hyRequest.delete({ url: `/social/reply/${replyId}` });
  return ok(null);
}

async function toggleCommentLikeApiImpl(
  commentId: string,
  targetLiked: boolean,
): Promise<IDataType<{ likeCount: number; liked: boolean }>> {
  let liked = false;
  let likeCount = 0;

  try {
    const [check, detail] = await Promise.all([
      readCommentLikeCheck(commentId),
      hyRequest.get<IDataType<ICommentRaw>>({
        url: `/social/comment/${commentId}`,
      }),
    ]);
    liked = check;
    likeCount = Number(detail.data?.likeCount ?? 0);
  } catch (err) {
    throw Object.assign(new Error('无法获取评论点赞状态，请稍后重试'), {
      cause: err,
    });
  }

  if (liked === targetLiked) {
    return ok({ liked, likeCount });
  }

  await mutateCommentLike(commentId, targetLiked);

  try {
    const [check, detail] = await Promise.all([
      readCommentLikeCheck(commentId),
      hyRequest.get<IDataType<ICommentRaw>>({
        url: `/social/comment/${commentId}`,
      }),
    ]);
    liked = check;
    likeCount = Number(detail.data?.likeCount ?? 0);
  } catch (err) {
    throw Object.assign(new Error('评论点赞失败，请稍后重试'), { cause: err });
  }

  if (liked !== targetLiked) {
    throw new Error('评论点赞状态未同步，请刷新后重试');
  }

  return ok({ liked, likeCount });
}

async function toggleReplyLikeApiImpl(
  replyId: string,
  targetLiked: boolean,
  currentLikeCount: number,
): Promise<IDataType<{ likeCount: number; liked: boolean }>> {
  // 点赞/取消点赞接口本身是幂等的，不要把只读状态接口作为操作前置条件。
  // 回复详情或 check 接口偶发失败时，不能因此阻止实际点赞操作。
  await mutateReplyLike(replyId, targetLiked);

  try {
    const detail = await hyRequest.get<IDataType<IReplyRaw>>({
      url: `/social/reply/${replyId}`,
    });
    return ok({
      liked: detail.data?.liked ?? targetLiked,
      likeCount: Number(detail.data?.likeCount ?? currentLikeCount),
    });
  } catch {
    // 写操作已经成功；读回详情失败时使用调用方的乐观计数，避免误回滚。
    const fallbackLikeCount = Math.max(
      0,
      currentLikeCount + (targetLiked ? 1 : -1),
    );
    const confirmedLiked = await readReplyLikeCheck(replyId).catch(() => null);
    return ok({
      liked: confirmedLiked ?? targetLiked,
      likeCount: fallbackLikeCount,
    });
  }
}

export async function toggleCommentLikeApi(
  articleId: string,
  commentId: string,
  liked: boolean,
): Promise<IDataType<{ likeCount: number; liked: boolean }>> {
  if (ENABLE_MOCK) {
    const comment = (commentStore[articleId] || []).find(
      (c) => c.id === commentId,
    );
    if (!comment) throw new Error('评论不存在');
    const wasLiked = comment.liked;
    comment.liked = liked;
    if (wasLiked !== liked) {
      comment.likeCount = Math.max(0, comment.likeCount + (liked ? 1 : -1));
    }
    return delay({ likeCount: comment.likeCount, liked: comment.liked });
  }

  return enqueueCommentLikeAction(commentId, () =>
    toggleCommentLikeApiImpl(commentId, liked),
  );
}

export async function toggleReplyLikeApi(
  articleId: string,
  commentId: string,
  replyId: string,
  liked: boolean,
  currentLikeCount = 0,
): Promise<IDataType<{ likeCount: number; liked: boolean }>> {
  if (ENABLE_MOCK) {
    const comment = (commentStore[articleId] || []).find(
      (c) => c.id === commentId,
    );
    const reply = comment?.replies.find((r) => r.id === replyId);
    if (!reply) throw new Error('回复不存在');
    const wasLiked = reply.liked;
    reply.liked = liked;
    if (wasLiked !== liked) {
      reply.likeCount = Math.max(0, reply.likeCount + (liked ? 1 : -1));
    }
    return delay({ likeCount: reply.likeCount, liked: reply.liked });
  }

  return enqueueReplyLikeAction(replyId, () =>
    toggleReplyLikeApiImpl(replyId, liked, currentLikeCount),
  );
}

export async function createRepostApi(payload: {
  refArticleId: string;
  title?: string;
  content?: string;
  /** @deprecated 使用 content */
  quote?: string;
  user: { accountId: number; nickname: string; avatar?: string };
}): Promise<IDataType<{ post: PostDetailData; refShareCount: number }>> {
  if (ENABLE_MOCK) {
    const ref = detailStore[payload.refArticleId];
    if (!ref) throw new Error('原帖不存在');
    const defaultTitle = buildDefaultRepostTitle(ref.title);
    const defaultContent = buildDefaultRepostContent(ref.title);
    const title = resolveShareTitle(payload.title, defaultTitle);
    const content = resolveShareContent(
      payload.content ?? payload.quote,
      defaultContent,
    );
    const id = String(++repostSeq);
    const post: PostDetailData = {
      id,
      postType: 'repost',
      title,
      content,
      categoryName: '转发',
      tags: [{ text: '转发' }],
      author: {
        accountId: payload.user.accountId,
        nickname: payload.user.nickname,
        avatar: payload.user.avatar,
      },
      createdAt: '刚刚',
      refPost: {
        id: ref.id,
        title: ref.title,
        summary: ref.content.slice(0, 80),
        coverUrl: ref.coverUrl || ref.images?.[0],
        videoUrl: ref.videoUrl,
        postType: ref.postType === 'repost' ? 'image_text' : ref.postType,
        author: ref.author,
      },
      stats: {
        viewCount: 1,
        likeCount: 0,
        commentCount: 0,
        favoriteCount: 0,
        shareCount: 0,
        liked: false,
        favorited: false,
      },
      followedAuthor: false,
    };
    detailStore[id] = post;
    commentStore[id] = [];
    ref.stats.shareCount += 1;
    return delay({
      post: structuredClone(post),
      refShareCount: ref.stats.shareCount,
    });
  }

  const refDetail = await getArticleDetailApi(payload.refArticleId);
  const ref = refDetail.data;
  let categoryId = ref.categoryId;
  if (!categoryId) {
    const cats = await listCategoriesApi(1, 20);
    categoryId = cats.data?.[0]?.id;
  }
  if (!categoryId) throw new Error('缺少分区，无法转发');

  const quote = resolveShareContent(
    payload.content ?? payload.quote,
    buildDefaultRepostContent(ref.title),
  );
  const title = resolveShareTitle(
    payload.title,
    buildDefaultRepostTitle(ref.title),
  );
  const saveRes = await saveArticleApi({
    title: title.slice(0, 80),
    summary: quote.slice(0, 200),
    content: quote,
    categoryId,
    postType: POST_TYPE.REPOST,
    refArticleId: payload.refArticleId,
    status: ARTICLE_STATUS.PENDING,
  });
  const newId = saveRes.data;
  const share = await recordShareApi(payload.refArticleId, 'repost');
  const detail = await fetchPostDetailApi(String(newId));
  if (!detail.data) throw new Error('转发成功但拉取详情失败');
  return ok({ post: detail.data, refShareCount: share.data.shareCount });
}

export async function checkFollowByAccountApi(
  targetAccountId: number,
): Promise<IDataType<{ followed: boolean }>> {
  if (ENABLE_MOCK) return delay({ followed: false });
  const res = await hyRequest.get<IDataType<boolean>>({
    url: `/social/follow/check/by-account/${targetAccountId}`,
  });
  return ok({ followed: Boolean(res.data) });
}

export async function reportTargetApi(payload: {
  targetType: 'article' | 'comment' | 'reply' | 'user' | 'danmaku';
  targetId: string;
  reason: string;
}): Promise<IDataType<null>> {
  if (ENABLE_MOCK) return delay(null);
  const typeMap = {
    article: 1,
    comment: 2,
    reply: 3,
    user: 4,
    danmaku: 5,
  } as const;
  await hyRequest.post({
    url: '/report',
    data: {
      targetType: typeMap[payload.targetType],
      targetId: payload.targetId,
      reason: payload.reason,
    },
  });
  return ok(null);
}

/** 通用问题反馈：复用举报链路，以站点反馈目标类型进入管理员审核中心。 */
export async function submitFeedbackApi(payload: {
  feedbackType: string;
  content: string;
}): Promise<IDataType<null>> {
  const reason = `反馈类型：${payload.feedbackType.trim()}\n具体内容：${payload.content.trim()}`;
  if (ENABLE_MOCK) return delay(null);
  await hyRequest.post({
    url: '/report',
    data: {
      targetType: 6,
      targetId: '0',
      reason,
    },
  });
  return ok(null);
}

/** 个人页：我的收藏 */
export async function fetchMyFavoritesApi(
  page = 1,
  size = 20,
): Promise<IPageResult<FeedItemData>> {
  if (ENABLE_MOCK) {
    const all = MOCK_FEEDS.favorites;
    const start = (page - 1) * size;
    return {
      code: 200,
      message: 'success',
      data: all.slice(start, start + size),
      page,
      size,
      total: all.length,
    };
  }

  const pageRes = await hyRequest.get<IPageResult<IArticleRaw>>({
    url: '/social/favorite/article/list',
    params: { page, size },
  });
  const data = await mapArticlesPageToFeedItems(pageRes.data || [], {
    createdAtOf: (raw) => raw.actionTime,
    sortTimeOf: (raw) => raw.actionTime,
  });
  return {
    ...pageRes,
    data,
  };
}

async function fetchMyActivityRows(
  url: string,
  limit: number,
  itemType: 'comment' | 'reply' | 'article',
): Promise<{ rows: IMyCommentRaw[]; total: number }> {
  try {
    const pageRes = await hyRequest.get<IPageResult<IMyCommentRaw>>({
      url,
      params: { page: 1, size: limit },
    });
    return {
      rows: (pageRes.data || []).map((row) =>
        normalizeMyCommentRow({ ...row, itemType: row.itemType || itemType }),
      ),
      total: Number(pageRes.total ?? 0),
    };
  } catch {
    return { rows: [], total: 0 };
  }
}

function sortFeedItems(items: FeedItemData[]): FeedItemData[] {
  return [...items].sort((a, b) =>
    (b.sortTime || b.createdAt || '').localeCompare(
      a.sortTime || a.createdAt || '',
    ),
  );
}

function paginateFeedItems(
  items: FeedItemData[],
  page: number,
  size: number,
  total: number,
): IPageResult<FeedItemData> {
  const start = (page - 1) * size;
  return {
    code: 200,
    message: 'success',
    data: items.slice(start, start + size),
    page,
    size,
    total,
  };
}

/** 个人页：我赞过的 */
export async function fetchMyLikedArticlesApi(
  page = 1,
  size = 20,
): Promise<IPageResult<FeedItemData>> {
  const me = currentMeAuthor();
  if (ENABLE_MOCK) {
    const all = MOCK_FEEDS.liked.map((item) => ({ ...item, liked: true }));
    const start = (page - 1) * size;
    return {
      code: 200,
      message: 'success',
      data: all.slice(start, start + size),
      page,
      size,
      total: all.length,
    };
  }

  const fetchLimit = page * size;
  const [pageRes, likedComments, likedReplies] = await Promise.all([
    hyRequest.get<IPageResult<IArticleRaw>>({
      url: '/social/like/article/list',
      params: { page: 1, size: fetchLimit },
    }),
    fetchMyActivityRows('/social/like/comment/my/list', fetchLimit, 'comment'),
    fetchMyActivityRows('/social/like/reply/my/list', fetchLimit, 'reply'),
  ]);
  const articleFeeds = await mapArticlesPageToFeedItems(pageRes.data || [], {
    liked: true,
    createdAtOf: (raw) => raw.actionTime,
    sortTimeOf: (raw) => raw.actionTime,
  });
  const activityRows = [...likedComments.rows, ...likedReplies.rows].sort(
    (a, b) => (b.createTime || '').localeCompare(a.createTime || ''),
  );
  const activityFeeds = await mapMyCommentRowsToFeedItems(activityRows, me, {
    activityShowAuthor: false,
    activityMode: 'liked',
  });
  const merged = sortFeedItems([...articleFeeds, ...activityFeeds]);
  const total =
    Number(pageRes.total ?? 0) + likedComments.total + likedReplies.total;
  return paginateFeedItems(merged, page, size, total);
}

interface IBrowseHistoryRaw {
  articleId: number;
  browseTime?: string;
  article?: IArticleRaw;
}

/** 个人页：浏览历史 */
export async function fetchBrowseHistoryApi(
  page = 1,
  size = 20,
): Promise<IPageResult<FeedItemData>> {
  if (ENABLE_MOCK) {
    const all = MOCK_FEEDS.history;
    const start = (page - 1) * size;
    return {
      code: 200,
      message: 'success',
      data: all.slice(start, start + size),
      page,
      size,
      total: all.length,
    };
  }

  const pageRes = await hyRequest.get<IPageResult<IBrowseHistoryRaw>>({
    url: '/social/browse/history',
    params: { page, size },
  });
  const records = pageRes.data || [];
  const articles = records
    .map((item) => item.article)
    .filter((item): item is IArticleRaw => Boolean(item));
  const browseTimeMap = new Map(
    records
      .filter((item) => item.article)
      .map((item) => [item.article!.id, item.browseTime]),
  );
  const feeds = await mapArticlesPageToFeedItems(articles, {
    createdAtOf: (raw) => browseTimeMap.get(raw.id),
    sortTimeOf: (raw) => browseTimeMap.get(raw.id),
  });
  const feedByArticleId = new Map(feeds.map((feed) => [feed.id, feed]));
  const data = records
    .map((item) => {
      const publicId = item.article?.publicId;
      return feedByArticleId.get(publicId) || null;
    })
    .filter((item): item is FeedItemData => Boolean(item));
  return {
    ...pageRes,
    data,
  };
}

interface IMyCommentRaw {
  id: number;
  articleId: number;
  articlePublicId?: string;
  articleTitle: string;
  content: string;
  likeCount?: number;
  liked?: boolean;
  createTime?: string;
  itemType?: 'comment' | 'reply' | 'article';
  parentCommentId?: number;
  parentCommentContent?: string;
  parentUserNickname?: string;
  parentUserId?: number;
  parentUserAccountId?: number;
  parentUserAvatar?: string;
  replyToUserNickname?: string;
  authorNickname?: string;
  authorUserId?: number;
  authorAccountId?: number;
  authorAvatar?: string;
  likerUserId?: number;
  likerNickname?: string;
  likerAvatar?: string;
  likerAccountId?: number;
}

type ActivityFeedMode = 'mine' | 'liked' | 'received';

function buildMineActivityQuote(row: IMyCommentRaw): string {
  const content = sanitizeGameShareContent({ content: row.content });
  const prefix = row.itemType === 'reply' ? '我的回复：' : '我的评论：';
  return `${prefix}${content}`;
}

function buildReceivedActivityQuote(row: IMyCommentRaw): string {
  if (row.itemType === 'article') {
    const title = row.articleTitle?.trim();
    return title ? `赞了你的帖子：${title}` : '赞了你的帖子';
  }
  const content = sanitizeGameShareContent({ content: row.content });
  if (row.itemType === 'reply') {
    return content ? `赞了你的回复：${content}` : '赞了你的回复';
  }
  return content ? `赞了你的评论：${content}` : '赞了你的评论';
}

function toActivityActor(
  row: Pick<
    IMyCommentRaw,
    'likerUserId' | 'likerNickname' | 'likerAvatar' | 'likerAccountId'
  >,
  userMap: Map<number, ReturnType<typeof authorFrom>>,
) {
  if (!row.likerUserId) return undefined;
  const resolved = row.likerAccountId
    ? userMap.get(row.likerAccountId)
    : undefined;
  const nickname = row.likerNickname?.trim() || resolved?.nickname;
  if (!nickname) return undefined;
  return {
    accountId: row.likerAccountId ?? resolved?.accountId ?? 0,
    nickname,
    avatar: resolved?.avatar || row.likerAvatar,
  };
}

function toActivitySubject(
  row: Pick<
    IMyCommentRaw,
    | 'authorUserId'
    | 'authorNickname'
    | 'authorAvatar'
    | 'authorAccountId'
    | 'content'
  >,
  userMap: Map<number, ReturnType<typeof authorFrom>>,
) {
  const resolved = row.authorAccountId
    ? userMap.get(row.authorAccountId)
    : undefined;
  const nickname = row.authorNickname?.trim() || resolved?.nickname;
  if (!nickname) return undefined;
  return {
    accountId: row.authorAccountId ?? resolved?.accountId ?? 0,
    nickname,
    avatar: resolved?.avatar || row.authorAvatar,
    content: sanitizeGameShareContent({ content: row.content }),
  };
}

function normalizeMyCommentRow(row: IMyCommentRaw): IMyCommentRaw {
  if (row.itemType === 'article') {
    return { ...row, itemType: 'article' };
  }
  const isReply =
    row.itemType === 'reply' ||
    (row.parentCommentId != null &&
      row.parentCommentId > 0 &&
      row.parentCommentId !== row.id);
  return {
    ...row,
    itemType: isReply ? 'reply' : 'comment',
  };
}

async function fetchMyReplyRows(limit: number): Promise<{
  rows: IMyCommentRaw[];
  total: number;
}> {
  try {
    const pageRes = await hyRequest.get<IPageResult<IMyCommentRaw>>({
      url: '/social/reply/my/list',
      params: { page: 1, size: limit },
    });
    return {
      rows: (pageRes.data || []).map((row) =>
        normalizeMyCommentRow({ ...row, itemType: 'reply' }),
      ),
      total: Number(pageRes.total ?? pageRes.data?.length ?? 0),
    };
  } catch {
    return { rows: [], total: 0 };
  }
}

async function mapMyCommentRowsToFeedItems(
  rows: IMyCommentRaw[],
  me: ContentCardAuthor,
  options?: { activityShowAuthor?: boolean; activityMode?: ActivityFeedMode },
): Promise<FeedItemData[]> {
  const mode = options?.activityMode ?? 'mine';
  const quoteOnly = mode === 'liked' || mode === 'received';
  const normalized = rows
    .map(normalizeMyCommentRow)
    .filter((row): row is IMyCommentRaw & { articlePublicId: string } =>
      Boolean(row.articlePublicId),
    );
  const publicArticleIds = Array.from(
    new Set(
      normalized
        .map((item) => item.articlePublicId)
        .filter((id): id is string => Boolean(id)),
    ),
  );
  const articleMap = await resolveArticlesByIds(publicArticleIds);
  const [statsList, authors] = await Promise.all([
    fetchStatsBatch(publicArticleIds),
    resolveAuthorsFromArticles(Array.from(articleMap.values())),
  ]);
  const statsMap = new Map(
    statsList
      .filter((s): s is IArticleStatsRaw & { publicId: string } =>
        Boolean(s.publicId),
      )
      .map((s) => [s.publicId, s]),
  );
  const relatedAccountIds = new Set<number>();
  normalized.forEach((row) => {
    if (row.likerAccountId) relatedAccountIds.add(row.likerAccountId);
    if (row.authorAccountId) relatedAccountIds.add(row.authorAccountId);
    if (row.parentUserAccountId) relatedAccountIds.add(row.parentUserAccountId);
  });
  const relatedUsers = await resolveUsersByAccountIds(
    Array.from(relatedAccountIds),
  );
  const data: FeedItemData[] = [];

  normalized.forEach((row) => {
    if (row.itemType === 'article') {
      const publicId = row.articlePublicId;
      const raw = articleMap.get(publicId);
      const stats = statsMap.get(publicId);
      const refPost = raw
        ? buildRefPostFromArticle(
            raw,
            resolveArticleAuthor(raw, authors),
            stats,
          )
        : buildUnavailableRefPost(publicId, 'unavailable', row.articleTitle);
      const activityActor =
        mode === 'received' ? toActivityActor(row, relatedUsers) : undefined;
      data.push(
        wrapActivityFeedItem(refPost, {
          id: `article-like-${row.id}${
            mode === 'received' && row.likerUserId
              ? `-liker-${row.likerUserId}`
              : ''
          }`,
          activityAuthor: me,
          activityLabel: '',
          quote: mode === 'received' ? buildReceivedActivityQuote(row) : '',
          activityType: 'comment',
          activityShowAuthor: false,
          activityShowRefPost: true,
          activityActor,
          createdAt: formatCardTime(row.createTime),
          sortTime: row.createTime,
          targetArticleId: publicId,
          likeCount: Number(row.likeCount || 0),
          liked: Boolean(row.liked),
        }),
      );
      return;
    }

    const publicId = row.articlePublicId;
    const raw = articleMap.get(publicId);
    const stats = statsMap.get(publicId);
    const refPost = raw
      ? buildRefPostFromArticle(raw, resolveArticleAuthor(raw, authors), stats)
      : buildUnavailableRefPost(publicId, 'unavailable', row.articleTitle);
    const isReply = row.itemType === 'reply';
    const parentNickname = row.parentUserNickname || row.replyToUserNickname;
    const parentUser = row.parentUserId
      ? relatedUsers.get(row.parentUserId)
      : undefined;
    const activityActor =
      mode === 'received' ? toActivityActor(row, relatedUsers) : undefined;
    const activitySubject =
      mode === 'liked' ? toActivitySubject(row, relatedUsers) : undefined;
    const quote =
      mode === 'received'
        ? buildReceivedActivityQuote(row)
        : mode === 'liked' && activitySubject
          ? `${activitySubject.nickname}：${activitySubject.content}`
          : buildMineActivityQuote(row);

    data.push(
      wrapActivityFeedItem(refPost, {
        id: `${isReply ? 'reply' : 'comment'}-${row.id}${
          mode === 'received' && row.likerUserId
            ? `-liker-${row.likerUserId}`
            : ''
        }`,
        activityAuthor: me,
        activityLabel: '',
        activityType: isReply ? 'reply' : 'comment',
        activityShowAuthor: false,
        activityShowRefPost: true,
        quote,
        activityActor,
        activitySubject,
        parentQuote:
          !quoteOnly && isReply && parentNickname && row.parentCommentContent
            ? {
                nickname: parentNickname,
                content: sanitizeGameShareContent({
                  content: row.parentCommentContent,
                }),
                accountId:
                  row.parentUserAccountId ?? parentUser?.accountId ?? 0,
                avatar: row.parentUserAvatar ?? parentUser?.avatar,
              }
            : undefined,
        createdAt: formatCardTime(row.createTime),
        sortTime: row.createTime,
        targetArticleId: publicId,
        commentId: String(isReply ? row.parentCommentId || row.id : row.id),
        replyId: isReply ? String(row.id) : undefined,
        likeCount: Number(row.likeCount || 0),
        liked: Boolean(row.liked),
      }),
    );
  });

  return data;
}

async function buildMockCommentFeedItems(): Promise<FeedItemData[]> {
  const me = currentMeAuthor();
  return MOCK_FEEDS.comments.flatMap((item) => {
    const articleId = item.targetArticleId || '1001';
    const post = detailStore[articleId];
    if (!post) return [];

    const refPost = {
      id: post.id,
      title: post.title,
      summary: post.content.slice(0, 120),
      coverUrl: post.coverUrl || post.images?.[0],
      videoUrl: post.videoUrl,
      postType:
        post.postType === 'repost' ? ('image_text' as const) : post.postType,
      author: {
        accountId: post.author.accountId,
        nickname: post.author.nickname,
        avatar: post.author.avatar,
      },
      viewCount: post.stats.viewCount,
      commentCount: post.stats.commentCount,
      likeCount: post.stats.likeCount,
    };

    const isReply = Boolean(item.replyId);

    return [
      wrapActivityFeedItem(refPost, {
        id: String(item.id),
        activityAuthor: me,
        activityLabel: '',
        activityType: isReply ? 'reply' : 'comment',
        activityShowAuthor: true,
        quote: item.content,
        parentQuote: item.parentQuote,
        createdAt: item.createdAt || '',
        targetArticleId: articleId,
        commentId: item.commentId || String(item.id),
        replyId: item.replyId,
        likeCount: item.likeCount,
        liked: item.liked,
      }),
    ];
  });
}

/** 个人页：我的评论 */
export async function fetchMyCommentsApi(
  page = 1,
  size = 20,
): Promise<IPageResult<FeedItemData>> {
  const me = currentMeAuthor();
  if (ENABLE_MOCK) {
    const all = await buildMockCommentFeedItems();
    const start = (page - 1) * size;
    return {
      code: 200,
      message: 'success',
      data: all.slice(start, start + size),
      page,
      size,
      total: all.length,
    };
  }

  const pageRes = await hyRequest.get<IPageResult<IMyCommentRaw>>({
    url: '/social/comment/my/list',
    params: { page: 1, size: page * size },
  });
  const replyFeed = await fetchMyReplyRows(page * size);
  const merged = [
    ...(pageRes.data || []).map((row) =>
      normalizeMyCommentRow({ ...row, itemType: 'comment' }),
    ),
    ...replyFeed.rows,
  ].sort((a, b) => (b.createTime || '').localeCompare(a.createTime || ''));

  const start = (page - 1) * size;
  const slice = merged.slice(start, start + size);
  const data = await mapMyCommentRowsToFeedItems(slice, me, {
    activityMode: 'mine',
  });
  const commentTotal = Number(pageRes.total ?? pageRes.data?.length ?? 0);

  return {
    ...pageRes,
    data,
    page,
    size,
    total: commentTotal + replyFeed.total,
  };
}

/** 个人页：获赞（他人赞了我的帖子/评论） */
export async function fetchMyReceivedLikesApi(
  page = 1,
  size = 20,
): Promise<IPageResult<FeedItemData>> {
  const me = currentMeAuthor();
  if (ENABLE_MOCK) {
    const all = MOCK_FEEDS.received || [];
    const start = (page - 1) * size;
    return {
      code: 200,
      message: 'success',
      data: all.slice(start, start + size),
      page,
      size,
      total: all.length,
    };
  }

  const fetchLimit = page * size;

  const [receivedArticles, receivedComments, receivedReplies] =
    await Promise.all([
      fetchMyActivityRows(
        '/social/like/article/received/list',
        fetchLimit,
        'article',
      ),
      fetchMyActivityRows(
        '/social/like/comment/received/list',
        fetchLimit,
        'comment',
      ),
      fetchMyActivityRows(
        '/social/like/reply/received/list',
        fetchLimit,
        'reply',
      ),
    ]);
  const activityRows = [
    ...receivedArticles.rows,
    ...receivedComments.rows,
    ...receivedReplies.rows,
  ].sort((a, b) => (b.createTime || '').localeCompare(a.createTime || ''));
  const activityFeeds = await mapMyCommentRowsToFeedItems(activityRows, me, {
    activityShowAuthor: false,
    activityMode: 'received',
  });
  const merged = sortFeedItems(activityFeeds);
  const total =
    receivedArticles.total + receivedComments.total + receivedReplies.total;
  return paginateFeedItems(merged, page, size, total);
}

/** 获赞总数：与获赞 Tab 同源（帖子/评论/回复被赞事件数） */
async function fetchReceivedLikesCount(): Promise<number> {
  try {
    const res = await hyRequest.get<
      IDataType<number | { count?: number; likeCount?: number; total?: number }>
    >({
      url: '/social/like/received/count',
    });
    const raw = res.data;
    const direct =
      typeof raw === 'number'
        ? raw
        : Number(raw?.count ?? raw?.likeCount ?? raw?.total ?? NaN);
    if (!Number.isNaN(direct) && direct >= 0) return direct;
  } catch {
    // 专用计数接口未实现
  }

  const [receivedArticles, receivedComments, receivedReplies] =
    await Promise.all([
      fetchMyActivityRows('/social/like/article/received/list', 1, 'article'),
      fetchMyActivityRows('/social/like/comment/received/list', 1, 'comment'),
      fetchMyActivityRows('/social/like/reply/received/list', 1, 'reply'),
    ]);
  return (
    receivedArticles.total + receivedComments.total + receivedReplies.total
  );
}

type FollowCountPayload = {
  following?: number;
  fans?: number;
};

function parseFollowCounts(data?: FollowCountPayload | null) {
  return {
    following: Number(data?.following ?? 0),
    followers: Number(data?.fans ?? 0),
  };
}

async function fetchFollowCountsByAccount(
  accountId: number,
): Promise<{ following: number; followers: number }> {
  try {
    const countRes = await hyRequest.get<IDataType<FollowCountPayload>>({
      url: `/social/follow/count/by-account/${accountId}`,
    });
    return parseFollowCounts(countRes.data);
  } catch {
    return { following: 0, followers: 0 };
  }
}

async function fetchFavoriteArticleCount(): Promise<number> {
  try {
    const favoriteRes = await hyRequest.get<IPageResult<unknown>>({
      url: '/social/favorite/article/list',
      params: { page: 1, size: 1 },
    });
    return Number(favoriteRes.total ?? 0);
  } catch {
    return 0;
  }
}

export async function fetchProfileSocialStatsByAccountApi(
  accountId: number,
): Promise<ProfileStats> {
  if (ENABLE_MOCK) {
    return MOCK_STATS;
  }

  const { following, followers } = await fetchFollowCountsByAccount(accountId);

  const [likes, favorites] = await Promise.all([
    fetchReceivedLikesCount().catch(() => 0),
    fetchFavoriteArticleCount(),
  ]);

  return {
    following,
    followers,
    likes,
    favorites,
  };
}

async function fetchFollowUserListPage(
  url: string,
  page: number,
  size: number,
): Promise<IPageResult<IUserCard>> {
  const pageRes = await hyRequest.get<IPageResult<IUserCard>>({
    url,
    params: { page, size },
  });
  return {
    ...pageRes,
    data: pageRes.data || [],
  };
}

/** 关注列表 */
export async function fetchFollowingListApi(
  page = 1,
  size = 20,
): Promise<IPageResult<IUserCard>> {
  if (ENABLE_MOCK) {
    const start = (page - 1) * size;
    return {
      code: 200,
      message: 'success',
      data: MOCK_FOLLOW_USERS.slice(start, start + size),
      page,
      size,
      total: MOCK_FOLLOW_USERS.length,
    };
  }
  return fetchFollowUserListPage('/social/follow/list', page, size);
}

/** 粉丝列表 */
export async function fetchFollowersListApi(
  page = 1,
  size = 20,
): Promise<IPageResult<IUserCard>> {
  if (ENABLE_MOCK) {
    const start = (page - 1) * size;
    return {
      code: 200,
      message: 'success',
      data: MOCK_FAN_USERS.slice(start, start + size),
      page,
      size,
      total: MOCK_FAN_USERS.length,
    };
  }
  return fetchFollowUserListPage('/social/follow/fans', page, size);
}
