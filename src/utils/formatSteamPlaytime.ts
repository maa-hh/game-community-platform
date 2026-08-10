/** 将 Steam 游玩时长（分钟）格式化为可读文案 */
export function formatSteamPlaytime(minutes?: number | null): string {
  const value = Number(minutes ?? 0);
  if (!Number.isFinite(value) || value <= 0) {
    return '0 分钟';
  }
  if (value < 60) {
    return `${Math.round(value)} 分钟`;
  }
  const hours = Math.floor(value / 60);
  const rest = Math.round(value % 60);
  if (rest === 0) {
    return `${hours} 小时`;
  }
  return `${hours} 小时 ${rest} 分钟`;
}

export function formatSteamAchievementProgress(
  unlocked?: number | null,
  total?: number | null,
): string | null {
  if (total == null || total <= 0) {
    return null;
  }
  const done = unlocked ?? 0;
  return `${done}/${total}`;
}
