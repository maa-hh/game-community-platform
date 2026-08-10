/** 帖子展示用评论总数 = 主评论数 + 回复数 */
export function resolveDisplayCommentCount(
  stats?: {
    commentCount?: number;
    replyCount?: number;
  } | null,
): number {
  return Number(stats?.commentCount ?? 0) + Number(stats?.replyCount ?? 0);
}
