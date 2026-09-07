import hyRequest from '@/service/request';
import { getUsersByAccountIdsApi } from '@/service/account';
import { checkFollowByAccountApi } from '@/service/social';
import type { IDataType } from '@/service/types';
import type { INotificationMessage } from '@/types/notification';
import { NOTIFICATION_EVENT } from '@/types/notification';
import { fetchArticlesRawByIds } from '@/utils/hydrateArticleForGameRepost';
import type { ICommentRaw, IReplyRaw } from '@/utils/mapPost';
import { buildNotificationCoverSources } from '@/utils/notificationCover';
import { fetchGameRepostMetaMap } from '@/utils/fetchGameRepostMeta';
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

function isCommentTarget(item: INotificationMessage): boolean {
  return (
    item.eventType === NOTIFICATION_EVENT.ARTICLE_COMMENT ||
    item.eventType === NOTIFICATION_EVENT.COMMENT_REPLY ||
    item.eventType === NOTIFICATION_EVENT.COMMENT_LIKE ||
    item.eventType === NOTIFICATION_EVENT.REPLY_LIKE
  );
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

  const gameAppIds = Array.from(
    new Set(
      items
        .map((item) => Number(item.gameAppId))
        .filter((id) => Number.isInteger(id) && id > 0),
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
        .filter(isCommentTarget)
        .map((item) => item.commentId)
        .filter((id): id is number => id != null && id > 0),
    ),
  );

  const replyIds = Array.from(
    new Set(
      items
        .filter(
          (item) =>
            isCommentTarget(item) &&
            (item.eventType === NOTIFICATION_EVENT.COMMENT_REPLY ||
              item.eventType === NOTIFICATION_EVENT.REPLY_LIKE),
        )
        .map((item) => item.replyId)
        .filter((id): id is number => id != null && id > 0),
    ),
  );

  const [
    articles,
    followResults,
    commentResults,
    replyResults,
    actorUsers,
    gameMetaMap,
  ] = await Promise.all([
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
    fetchGameRepostMetaMap(gameAppIds),
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

    const gameMeta = item.gameAppId
      ? gameMetaMap[Number(item.gameAppId)]
      : undefined;
    if (gameMeta) {
      next.gameCoverUrl = next.gameCoverUrl || gameMeta.coverUrl;
      next.gameTitle = next.gameTitle || gameMeta.name;
    }

    if (article) {
      const coverSource = publicId ? coverSourceMap.get(publicId) : undefined;
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

    if (!isCommentTarget(item)) return next;

    const isReplyTarget =
      item.eventType === NOTIFICATION_EVENT.COMMENT_REPLY ||
      item.eventType === NOTIFICATION_EVENT.REPLY_LIKE;

    if (isReplyTarget && item.replyId) {
      const reply = replyMap.get(item.replyId);
      const replyAccountId = reply?.accountId;
      // 历史通知的 actorAccountId 可能因通知服务补全失败而为空，
      // 但回复详情仍然携带真实的回复作者账号 ID。回复通知必须用
      // 这个 ID 作为 replyToAccountId，不能让调用方退化成 0。
      if (
        item.eventType === NOTIFICATION_EVENT.COMMENT_REPLY &&
        (!next.actorAccountId || next.actorAccountId <= 0) &&
        replyAccountId != null &&
        replyAccountId > 0
      ) {
        next.actorAccountId = replyAccountId;
      }
      if (item.eventType === NOTIFICATION_EVENT.COMMENT_REPLY) {
        next.actorUsername =
          next.actorUsername || reply?.username?.trim() || undefined;
        next.actorAvatar = next.actorAvatar || reply?.avatar;
      }
      if (reply) {
        next.liked = Boolean(reply.liked);
        next.likeCount = Number(reply.likeCount ?? 0);
      }
      if (reply?.content) {
        next.contentText = reply.content;
      }
      // 回复通知的 resultText 是后端保存的“新回复内容”，不能直接当作
      // 被回复的原文展示。原文需要根据通知里的 commentId 重新补全。
      if (
        item.eventType === NOTIFICATION_EVENT.COMMENT_REPLY &&
        item.commentId
      ) {
        const parent = commentMap.get(item.commentId);
        next.resultText = parent?.content || undefined;
      }
    } else if (item.commentId) {
      const comment = commentMap.get(item.commentId);
      const commentAccountId = comment?.accountId;
      // 评论通知同样优先使用评论详情中的作者账号，修复旧通知缺少
      // actorAccountId 时点击“回复”会带出无效用户 ID 的问题。
      if (
        item.eventType === NOTIFICATION_EVENT.ARTICLE_COMMENT &&
        (!next.actorAccountId || next.actorAccountId <= 0) &&
        commentAccountId != null &&
        commentAccountId > 0
      ) {
        next.actorAccountId = commentAccountId;
      }
      if (item.eventType === NOTIFICATION_EVENT.ARTICLE_COMMENT) {
        next.actorUsername =
          next.actorUsername || comment?.username?.trim() || undefined;
        next.actorAvatar = next.actorAvatar || comment?.avatar;
      }
      if (comment) {
        next.liked = Boolean(comment.liked);
        next.likeCount = Number(comment.likeCount ?? 0);
      }
      if (comment?.content) {
        next.contentText = comment.content;
      }
    }

    return next;
  });
}
