import hyRequest from '@/service/request';
import { getUsersByAccountIdsApi } from '@/service/account';
import { checkFollowByAccountApi } from '@/service/social';
import type { IDataType } from '@/service/types';
import type { INotificationMessage } from '@/types/notification';
import { NOTIFICATION_EVENT } from '@/types/notification';
import { fetchArticlesRawByIds } from '@/utils/hydrateArticleForGameRepost';
import type { ICommentRaw, IReplyRaw } from '@/utils/mapPost';
import { buildNotificationCoverSources } from '@/utils/notificationCover';
import { resolveNotificationCategory } from '@/utils/notificationCategory';
import { resolvePostCardTitle, resolvePostCoverMedia } from '@/utils/postCover';

async function fetchCommentRaw(commentId: number): Promise<ICommentRaw | null> {
  try {
    const res = await hyRequest.get<IDataType<ICommentRaw>>({
      url: `/social/comment/${commentId}`,
    });
    return res.data ?? null;
  } catch {
    return null;
  }
}

async function fetchReplyRaw(replyId: number): Promise<IReplyRaw | null> {
  try {
    const res = await hyRequest.get<IDataType<IReplyRaw>>({
      url: `/social/reply/${replyId}`,
    });
    return res.data ?? null;
  } catch {
    return null;
  }
}

function isCommentCategory(item: INotificationMessage): boolean {
  return resolveNotificationCategory(item.eventType) === 'comment';
}

/** 批量补全通知：帖子封面、评论正文、关注状态 */
export async function enrichNotificationMessages(
  items: INotificationMessage[],
): Promise<INotificationMessage[]> {
  if (items.length === 0) return items;

  const articleIds = Array.from(
    new Set(
      items
        .map((item) => item.articlePublicId)
        .filter((id): id is string => Boolean(id)),
    ),
  );

  const followActorAccountIds = Array.from(
    new Set(
      items
        .filter((item) => item.eventType === NOTIFICATION_EVENT.FOLLOW)
        .map((item) => item.actorAccountId)
        .filter((id): id is number => id != null && id > 0),
    ),
  );

  const actorAccountIds = Array.from(
    new Set(
      items
        .flatMap((item) => [
          item.actorAccountId,
          ...(item.aggregateActors || []).map((actor) => actor.accountId),
        ])
        .filter((id): id is number => id != null && id > 0),
    ),
  );

  const commentIds = Array.from(
    new Set(
      items
        .filter(isCommentCategory)
        .map((item) => item.commentId)
        .filter((id): id is number => id != null && id > 0),
    ),
  );

  const replyIds = Array.from(
    new Set(
      items
        .filter(
          (item) =>
            isCommentCategory(item) &&
            item.eventType === NOTIFICATION_EVENT.COMMENT_REPLY,
        )
        .map((item) => item.replyId)
        .filter((id): id is number => id != null && id > 0),
    ),
  );

  const [articles, followResults, commentResults, replyResults, actorUsers] =
    await Promise.all([
      fetchArticlesRawByIds(articleIds),
      Promise.all(
        followActorAccountIds.map(async (accountId) => {
          try {
            const res = await checkFollowByAccountApi(accountId);
            return [accountId, res.data.followed] as const;
          } catch {
            return [accountId, false] as const;
          }
        }),
      ),
      Promise.all(
        commentIds.map(async (id) => [id, await fetchCommentRaw(id)] as const),
      ),
      Promise.all(
        replyIds.map(async (id) => [id, await fetchReplyRaw(id)] as const),
      ),
      actorAccountIds.length > 0
        ? getUsersByAccountIdsApi(actorAccountIds)
            .then((res) => res.data || [])
            .catch(() => [])
        : Promise.resolve([]),
    ]);

  const articleMap = new Map(articles.map((row) => [row.publicId, row]));
  const coverSourceMap = await buildNotificationCoverSources(articles);
  const followMap = new Map(followResults);
  const commentMap = new Map(commentResults);
  const replyMap = new Map(replyResults);
  const actorUserMap = new Map(
    actorUsers.map((user) => [user.accountId, user]),
  );

  return items.map((item) => {
    const next: INotificationMessage = { ...item };
    const actor = item.actorAccountId
      ? actorUserMap.get(item.actorAccountId)
      : undefined;
    next.actorUsername = actor?.username || item.actorUsername || undefined;
    next.actorAvatar = actor?.avatar || item.actorAvatar;
    next.aggregateActors = item.aggregateActors?.map((aggregateActor) => {
      const aggregateUser = actorUserMap.get(aggregateActor.accountId);
      return {
        ...aggregateActor,
        username: aggregateUser?.username || aggregateActor.username,
        avatar: aggregateUser?.avatar || aggregateActor.avatar,
      };
    });
    const publicId = item.articlePublicId;
    const article = publicId ? articleMap.get(publicId) : undefined;

    if (article) {
      const coverSource = coverSourceMap.get(article.id);
      if (coverSource) {
        next.articleCoverSource = coverSource;
        next.articleTitle =
          next.articleTitle || resolvePostCardTitle(coverSource);
        const media = resolvePostCoverMedia(coverSource);
        next.articleCoverUrl = next.articleCoverUrl || media.coverUrl;
      } else {
        next.articleTitle = next.articleTitle || article.title;
      }
    }

    if (item.actorAccountId && item.eventType === NOTIFICATION_EVENT.FOLLOW) {
      next.actorFollowed =
        followMap.get(item.actorAccountId) ?? next.actorFollowed;
    }

    if (!isCommentCategory(item)) return next;

    if (item.eventType === NOTIFICATION_EVENT.COMMENT_REPLY && item.replyId) {
      const reply = replyMap.get(item.replyId);
      if (reply?.content) {
        next.contentText = reply.content;
      }
      if (!next.resultText?.trim() && item.commentId) {
        const parent = commentMap.get(item.commentId);
        if (parent?.content) {
          next.resultText = parent.content;
        }
      }
    } else if (item.commentId) {
      const comment = commentMap.get(item.commentId);
      if (comment?.content) {
        next.contentText = comment.content;
      }
    }

    return next;
  });
}
