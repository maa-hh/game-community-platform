/** Steam 评分展示（支持 0–10 分或 0–100 好评率） */
export function formatSteamScore(score?: number): string {
  // 目录表历史默认值为 0；在 Steam 指标尚未同步时它表示未知评分，
  // 不能在卡片上伪装成真实的“Steam 评分 0”。
  if (score == null || !Number.isFinite(score) || score <= 0) {
    return '暂无评分';
  }
  if (score > 0 && score <= 10) {
    return `${score.toFixed(1)} 分`;
  }
  if (score > 10 && score <= 100) {
    return `${Math.round(score)}% 好评`;
  }
  return String(score);
}

/** Steam 评价人数 */
export function formatSteamReviewCount(count?: number): string | null {
  if (count == null || !Number.isFinite(count) || count <= 0) {
    return null;
  }
  return `${count.toLocaleString('zh-CN')} 条评价`;
}

/** 本站用户评分 */
export function formatSiteScore(
  avgScore?: number,
  reviewCount?: number,
): string {
  if (
    avgScore != null &&
    Number.isFinite(avgScore) &&
    reviewCount != null &&
    reviewCount > 0
  ) {
    return `${Number(avgScore).toFixed(1)} 分`;
  }
  return '暂无评分';
}

export function formatGameStudio(
  developer?: string,
  publisher?: string,
): string {
  const parts = [developer?.trim(), publisher?.trim()].filter(
    (value, index, arr) => value && arr.indexOf(value) === index,
  ) as string[];
  return parts.length > 0 ? parts.join(' · ') : '暂无工作室信息';
}
