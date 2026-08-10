/** 帖子/卡片上的游戏标签 */
export interface IGameTag {
  appId: number;
  name: string;
  iconUrl?: string;
}

/** 游戏详情 */
export interface IGameScreenshot {
  thumbnailUrl?: string;
  fullUrl: string;
}

export interface IGameMovie {
  name?: string;
  thumbnailUrl?: string;
  mp4Url?: string;
  webmUrl?: string;
}

export interface IGamePrice {
  free?: boolean;
  currency?: string;
  initial?: number;
  finalPrice?: number;
  discountPercent?: number;
  /** ISO 8601 或 Unix 时间戳（秒/毫秒） */
  discountEndAt?: string | number;
  formatted?: string;
}

export interface IGameMetacritic {
  score?: number;
  url?: string;
}

export interface IGameAchievement {
  apiName?: string;
  name: string;
  description?: string;
  iconUrl?: string;
}

export interface IGameDetail {
  appId: number;
  name: string;
  /** Steam 富详情是否已经完成至少一次后台同步 */
  detailReady?: boolean;
  coverUrl?: string;
  shortDescription?: string;
  aboutHtml?: string;
  description?: string;
  steamUrl?: string;
  tags?: string[];
  categories?: string[];
  releaseDate?: string;
  developer?: string;
  publisher?: string;
  steamReviewScore?: number;
  steamReviewCount?: number;
  discussCount?: number;
  screenshots?: IGameScreenshot[];
  movies?: IGameMovie[];
  price?: IGamePrice;
  metacritic?: IGameMetacritic;
  achievementTotal?: number;
  achievementHighlights?: IGameAchievement[];
  pcRequirementsMin?: string;
  pcRequirementsRec?: string;
}

/** 游戏评分统计 */
export interface IGameRatingStats {
  appId: number;
  averageScore: number;
  reviewCount: number;
  /** 各分数段人数，key 为 1–10 */
  distribution?: Record<string, number>;
}

/** 用户对游戏的评价 */
export interface IGameReview {
  /** 对外短评标识，不能使用数据库自增 id。 */
  reviewId: string;
  accountId: number;
  username?: string;
  avatar?: string;
  appId: number;
  score: number;
  content?: string;
  likeCount?: number;
  replyCount?: number;
  liked?: boolean;
  createTime?: string;
  updateTime?: string;
}

export type GameReviewSort = 'latest' | 'hot';

export interface IGameReviewReply {
  replyId: string;
  reviewId: string;
  accountId: number;
  username?: string;
  avatar?: string;
  content: string;
  likeCount?: number;
  liked?: boolean;
  createTime?: string;
}

/** Steam 绑定资料 */
export interface ISteamProfile {
  steamId: string;
  personaName: string;
  avatarUrl?: string;
  profileUrl?: string;
  steamLevel?: number;
  bound?: boolean;
  libraryPublic?: boolean;
  gameCount?: number;
  librarySyncedAt?: string;
}

/** Steam 游戏库条目 */
export interface ISteamGameItem {
  appId: number;
  name: string;
  nameZh?: string;
  nameEn?: string;
  iconUrl?: string;
  coverUrl?: string;
  playtimeForever?: number;
  playtimeTwoWeeks?: number;
  achievementUnlocked?: number;
  achievementTotal?: number;
  syncedAt?: string;
  lastPlayedAt?: string;
}

/** Steam 游戏库单页增量同步结果 */
export interface ISteamLibrarySync {
  syncId?: string;
  page: number;
  nextPage: number;
  pageSize: number;
  total: number;
  batchCount: number;
  processedCount: number;
  libraryPublic: boolean;
  completed: boolean;
  hasMore: boolean;
}

/** Steam 用户单款游戏成就 */
export interface ISteamUserAchievement {
  apiName?: string;
  name: string;
  description?: string;
  iconUrl?: string;
  unlocked?: boolean;
  unlockTime?: string;
  /** 全球解锁率 0–100 */
  globalPercent?: number;
}

/** Steam 用户单款游戏统计 */
export interface ISteamGameStats {
  appId: number;
  name?: string;
  nameZh?: string;
  nameEn?: string;
  owned?: boolean;
  playtimeForever?: number;
  playtimeTwoWeeks?: number;
  lastPlayedAt?: string;
  achievementUnlocked?: number;
  achievementTotal?: number;
  achievements?: ISteamUserAchievement[];
  achievementStatus?:
    'READY' | 'LOADING' | 'SYNCING' | 'FAILED' | 'NOT_AVAILABLE' | string;
  achievementSyncedAt?: string;
  achievementNextRefreshAt?: string;
}

/** 游戏列表项（发现/搜索/卡片） */
export interface IGameListItem {
  appId: number;
  name: string;
  coverUrl?: string;
  genres?: string[];
  developer?: string;
  publisher?: string;
  releaseDate?: string;
  /** Steam 评分或好评率 */
  steamScore?: number;
  avgScore?: number;
  reviewCount?: number;
  discussCount?: number;
  price?: IGamePrice;
  /** Steam 评价总数 */
  steamReviewCount?: number;
  /** 榜单名次（仅国区榜） */
  rank?: number;
}

/** Steam 国区榜单类型 */
export type GameChartBoard = 'hot' | 'new' | 'free' | 'discount';

/** 发现页来源：全部 + 四榜 */
export type GameDiscoverBoard = 'all' | GameChartBoard;

export type GameDiscoverSort =
  'rank' | 'steam_score' | 'steam_reviews' | 'price' | 'discount_price';

export type GameDiscoverOrder = 'asc' | 'desc';

export interface IGameDiscoverQuery {
  board?: GameDiscoverBoard;
  sort?: GameDiscoverSort;
  order?: GameDiscoverOrder;
  page?: number;
  size?: number;
  minSteamScore?: number;
  maxSteamScore?: number;
  minSteamReviews?: number;
  maxSteamReviews?: number;
  /** 原价，单位：分 */
  minPrice?: number;
  maxPrice?: number;
  /** 现价，单位：分 */
  minFinalPrice?: number;
  maxFinalPrice?: number;
  minDiscount?: number;
  discountOnly?: boolean;
  freeOnly?: boolean;
}

/** 榜单列表项（含排名） */
export interface IGameChartItem extends IGameListItem {
  rank?: number;
}

/** 我的游戏（关注） */
export interface IUserGameItem {
  appId: number;
  name: string;
  coverUrl?: string;
  genres?: string[];
  developer?: string;
  publisher?: string;
  releaseDate?: string;
  steamScore?: number;
  avgScore?: number;
  reviewCount?: number;
  discussCount?: number;
  source?: 'manual' | 'steam_import' | 'discover' | string;
  steamOwned?: boolean;
  playtimeForever?: number;
  followTime?: string;
}

export interface IGameReviewSavePayload {
  score: number;
  content?: string;
}
