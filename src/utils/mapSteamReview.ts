type NumericValue = number | string | null | undefined;

function asRecord(value: unknown): Record<string, unknown> | null {
  return typeof value === 'object' && value !== null
    ? (value as Record<string, unknown>)
    : null;
}

function readPositiveNumber(
  source: Record<string, unknown>,
  key: string,
): number | undefined {
  const value = source[key] as NumericValue;
  if (value == null || value === '') return undefined;

  const number = Number(value);
  return Number.isFinite(number) && number > 0 ? number : undefined;
}

function readFirstPositiveNumber(
  sources: Record<string, unknown>[],
  keys: string[],
): number | undefined {
  for (const source of sources) {
    for (const key of keys) {
      const value = readPositiveNumber(source, key);
      if (value != null) return value;
    }
  }
  return undefined;
}

/**
 * Steam 的评价接口同时存在 snake_case、camelCase 和历史兼容字段。
 * review_percentage 是最适合展示的好评率；当它不存在时再退回 Steam 的
 * 1–10 评价分。部分旧接口还会返回 0 作为未同步占位值，必须跳过它。
 */
export function resolveSteamReviewMetrics(raw: unknown): {
  score?: number;
  count?: number;
} {
  const root = asRecord(raw);
  if (!root) return {};

  const sources = [
    root,
    root.steamReviews,
    root.steamReview,
    root.steamReviewStats,
    root.steamReviewSummary,
    root.querySummary,
    root.query_summary,
  ]
    .map(asRecord)
    .filter((value): value is Record<string, unknown> => !!value);

  const totalCount =
    readFirstPositiveNumber(sources, [
      'steamReviewCount',
      'totalReviews',
      'totalReviewCount',
      'reviewsCount',
      'total_reviews',
      'total_review_count',
      'reviews_count',
    ]) ??
    // root.reviewCount 是本站评分数量；只有嵌套的 Steam 评价对象才使用
    // 通用 reviewCount，避免把本站数据错标成 Steam 评价数。
    readFirstPositiveNumber(sources.slice(1), ['reviewCount']);
  const positiveCount = readFirstPositiveNumber(sources, [
    'totalPositive',
    'positiveReviews',
    'total_positive',
    'positive_reviews',
  ]);
  const negativeCount = readFirstPositiveNumber(sources, [
    'totalNegative',
    'negativeReviews',
    'total_negative',
    'negative_reviews',
  ]);
  const derivedPercentage =
    positiveCount != null && (totalCount ?? negativeCount) != null
      ? (positiveCount / (totalCount ?? positiveCount + (negativeCount ?? 0))) *
        100
      : undefined;
  const percentage =
    readFirstPositiveNumber(sources, [
      'steamReviewPercentage',
      'steamReviewPercent',
      'reviewPercentage',
      'reviewPercent',
      'review_percentage',
      'positivePercentage',
      'positive_percentage',
      'percentage',
    ]) ?? derivedPercentage;
  const score =
    (percentage != null && percentage <= 100 ? percentage : undefined) ??
    readFirstPositiveNumber(sources, [
      'steamReviewScore',
      'steamScore',
      'reviewScore',
      'review_score',
    ]);
  return { score, count: totalCount };
}
