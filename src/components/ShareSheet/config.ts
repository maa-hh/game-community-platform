export const REPOST_QUOTE_MAX = 500;

export const repostModalTips = {
  post: '将作为一条新动态发布，可自定义标题与正文（选填）。',
  game: '将作为一条新动态发布，可自定义标题与正文（选填）。',
} as const;

/** @deprecated 使用 repostModalTips.post */
export const repostModalTip = repostModalTips.post;

export const shareSheetWidth = 420;
