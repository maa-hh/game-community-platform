/**
 * 瀑布流卡片媒体区尺寸（与 FeedMasonryCard 下半区理论最大高度对齐）
 *
 * 下半区最大高度 ≈ 内边距 + 标题一行 + 摘要两行 + 标签一行 + 底栏
 */
export const FEED_CARD_BODY_PADDING_Y = 22;
export const FEED_CARD_TITLE_LINE_HEIGHT = 20.3; // 14px * 1.45
export const FEED_CARD_TITLE_LINES = 1;
export const FEED_CARD_TITLE_GAP = 4;
export const FEED_CARD_SUMMARY_LINE_HEIGHT = 18; // 12px * 1.5
export const FEED_CARD_SUMMARY_LINES = 2;
export const FEED_CARD_SUMMARY_GAP = 8;
export const FEED_CARD_TAGS_BLOCK = 28;
export const FEED_CARD_FOOTER_BLOCK = 22;

export const FEED_CARD_BODY_MAX_HEIGHT = Math.round(
  FEED_CARD_BODY_PADDING_Y +
    FEED_CARD_TITLE_LINE_HEIGHT * FEED_CARD_TITLE_LINES +
    FEED_CARD_TITLE_GAP +
    FEED_CARD_SUMMARY_LINE_HEIGHT * FEED_CARD_SUMMARY_LINES +
    FEED_CARD_SUMMARY_GAP +
    FEED_CARD_TAGS_BLOCK +
    FEED_CARD_FOOTER_BLOCK,
);

/** 上传封面展示最小高度（= 下半区理论最大高度） */
export const FEED_CARD_MEDIA_MIN_HEIGHT = FEED_CARD_BODY_MAX_HEIGHT;

/** 封面最大比例：宽:高 = 3:4 */
export const FEED_CARD_COVER_ASPECT_W = 3;
export const FEED_CARD_COVER_ASPECT_H = 4;

/** 标题海报最小比例：宽:高 = 2:1 */
export const FEED_CARD_POSTER_MIN_ASPECT_W = 2;
export const FEED_CARD_POSTER_MIN_ASPECT_H = 1;

/** 标题海报画布参考宽度 */
export const FEED_CARD_POSTER_CANVAS_WIDTH = 640;

/** 行卡标题海报统一生成宽度（推荐榜 / 个人页等） */
export const FEED_ROW_POSTER_WIDTH = 128;

/** 通知等紧凑缩略图展示宽度 */
export const NOTIFICATION_COVER_WIDTH = 56;

/** 按卡片宽计算封面上限高度（3:4） */
export function resolveFeedCoverMaxHeight(width: number): number {
  const w = Math.max(0, width);
  return Math.round((w * FEED_CARD_COVER_ASPECT_H) / FEED_CARD_COVER_ASPECT_W);
}

/** 标题海报最小高度（2:1） */
export function resolveFeedPosterMinHeight(width: number): number {
  const w = Math.max(0, width);
  return Math.round(
    (w * FEED_CARD_POSTER_MIN_ASPECT_H) / FEED_CARD_POSTER_MIN_ASPECT_W,
  );
}

/** 标题海报最大高度（3:4） */
export function resolveFeedPosterMaxHeight(width: number): number {
  return resolveFeedCoverMaxHeight(width);
}
