/** 统一数字展示：≥1万显示「x.x万」 */
export function formatCount(n: number): string {
  if (n >= 10000) {
    return `${(n / 10000).toFixed(n >= 100000 ? 0 : 1)}万`;
  }
  return String(n);
}

/** @deprecated 使用 formatCount */
export const formatStatCount = formatCount;
