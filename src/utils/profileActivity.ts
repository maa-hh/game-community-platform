import type { ContentCardAuthor } from '@/types/content';
import type { FeedItemData } from '@/types/profile';
import type { PostRefCard } from '@/types/post';
import {
  authorFrom,
  type IArticleRaw,
  type IArticleStatsRaw,
} from '@/utils/mapPost';
import { buildRefPostFromArticleRaw } from '@/utils/refPostCard';

export interface ActivityParentQuote {
  nickname: string;
  content: string;

  accountId?: number;
  avatar?: string;
}

export interface ActivityActor {
  accountId?: number;
  nickname: string;
  avatar?: string;
}

export interface ActivitySubjectQuote {
  accountId?: number;
  nickname: string;
  avatar?: string;
  content: string;
}

export interface WrapActivityOptions {
  id: string;
  activityAuthor: ContentCardAuthor;
  activityLabel: string;
  quote?: string;
  parentQuote?: ActivityParentQuote;
  createdAt: string;
  targetArticleId: string;
  commentId?: string;
  replyId?: string;
  likeCount?: number;
  liked?: boolean;
  sortTime?: string;
  activityType?: 'comment' | 'reply';
  activityShowAuthor?: boolean;
  activityShowRefPost?: boolean;
  activityActor?: ActivityActor;
  activitySubject?: ActivitySubjectQuote;
}

export function buildRefPostFromArticle(
  raw: IArticleRaw,
  author: ReturnType<typeof authorFrom>,
  stats?: IArticleStatsRaw | null,
): PostRefCard {
  return buildRefPostFromArticleRaw(
    raw,
    {
      accountId: author.accountId,
      nickname: author.nickname,
      avatar: author.avatar,
    },
    stats,
  );
}

export function wrapActivityFeedItem(
  refPost: PostRefCard,
  opts: WrapActivityOptions,
): FeedItemData {
  const activityType =
    opts.activityType ?? (opts.replyId ? 'reply' : 'comment');

  return {
    id: opts.id,
    author: opts.activityAuthor,
    title: opts.activityLabel,
    content: '',
    activityType,
    activityQuote: opts.quote?.trim() || '',
    activityShowAuthor: opts.activityShowAuthor ?? false,
    activityShowRefPost: opts.activityShowRefPost ?? true,
    activityActor: opts.activityActor,
    activitySubject: opts.activitySubject,
    parentQuote: opts.parentQuote,
    postType: 'repost',
    refPost,
    targetArticleId: opts.targetArticleId,
    commentId: opts.commentId,
    replyId: opts.replyId,
    createdAt: opts.createdAt,
    sortTime: opts.sortTime,
    likeCount: opts.likeCount,
    liked: opts.liked,
  };
}
