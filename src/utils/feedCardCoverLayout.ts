import {
  FEED_CARD_BODY_MAX_HEIGHT,
  FEED_CARD_MEDIA_MIN_HEIGHT,
  resolveFeedCoverMaxHeight,
} from '@/constants/feedCardMedia';

export type FeedCoverDisplayMode = 'natural' | 'crop';

export interface FeedCoverDisplayLayout {
  mode: FeedCoverDisplayMode;
  displayHeight: number;
}

/** 按卡片宽度等比缩放，不足最小高度或超过 3:4 上限时居中裁剪展示 */
export function resolveFeedCoverDisplay(
  containerWidth: number,
  naturalWidth: number,
  naturalHeight: number,
): FeedCoverDisplayLayout {
  const minH = FEED_CARD_MEDIA_MIN_HEIGHT;
  const maxH = resolveFeedCoverMaxHeight(containerWidth);

  if (containerWidth <= 0 || naturalWidth <= 0 || naturalHeight <= 0) {
    return { mode: 'crop', displayHeight: minH };
  }

  const scaledHeight = (naturalHeight * containerWidth) / naturalWidth;

  if (scaledHeight < minH) {
    return { mode: 'crop', displayHeight: minH };
  }
  if (scaledHeight > maxH) {
    return { mode: 'crop', displayHeight: maxH };
  }
  return { mode: 'natural', displayHeight: scaledHeight };
}

export function getFeedCardMediaCssVars(): Record<string, string> {
  return {
    '--feed-card-body-max-h': `${FEED_CARD_BODY_MAX_HEIGHT}px`,
    '--feed-card-media-min-h': `${FEED_CARD_MEDIA_MIN_HEIGHT}px`,
  };
}
