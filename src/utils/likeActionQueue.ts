/** 同一目标点赞操作全局串行，避免连点 POST/DELETE 竞态 */
const likeChains = new Map<string, Promise<unknown>>();

export function enqueueLikeAction<T>(
  key: string,
  action: () => Promise<T>,
): Promise<T> {
  const prev = likeChains.get(key) ?? Promise.resolve();
  const run = prev.catch(() => undefined).then(action);
  const tracked = run.finally(() => {
    if (likeChains.get(key) === tracked) {
      likeChains.delete(key);
    }
  });
  likeChains.set(key, tracked);
  return run;
}

export function enqueuePostLikeAction<T>(
  articleId: string,
  action: () => Promise<T>,
): Promise<T> {
  return enqueueLikeAction(`article:${articleId}`, action);
}

export function enqueueFavoriteAction<T>(
  articleId: string,
  action: () => Promise<T>,
): Promise<T> {
  return enqueueLikeAction(`favorite:${articleId}`, action);
}

export function enqueueCommentLikeAction<T>(
  commentId: string,
  action: () => Promise<T>,
): Promise<T> {
  return enqueueLikeAction(`comment:${commentId}`, action);
}

export function enqueueReplyLikeAction<T>(
  replyId: string,
  action: () => Promise<T>,
): Promise<T> {
  return enqueueLikeAction(`reply:${replyId}`, action);
}
