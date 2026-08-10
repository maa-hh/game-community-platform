/** 是否像可解析的日期时间字符串（ISO / `yyyy-MM-dd HH:mm`） */
function looksLikeDateTime(raw: string): boolean {
  return /^\d{4}-\d{2}-\d{2}/.test(raw) || raw.includes('T');
}

function pad2(n: number): string {
  return String(n).padStart(2, '0');
}

function calendarDayDiff(earlier: Date, later: Date): number {
  const startEarlier = new Date(
    earlier.getFullYear(),
    earlier.getMonth(),
    earlier.getDate(),
  );
  const startLater = new Date(
    later.getFullYear(),
    later.getMonth(),
    later.getDate(),
  );
  return Math.round(
    (startLater.getTime() - startEarlier.getTime()) / 86_400_000,
  );
}

function formatAbsolute(date: Date): string {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())} ${pad2(date.getHours())}:${pad2(date.getMinutes())}`;
}

/** 解析后端 / ISO 时间；无法解析时返回 null（如 mock 的「刚刚」） */
export function parseDateTime(value?: string | null): Date | null {
  if (!value?.trim()) return null;
  const raw = value.trim();
  if (!looksLikeDateTime(raw)) return null;

  const normalized = raw.includes('T') ? raw : raw.replace(' ', 'T');
  const date = new Date(normalized);
  if (Number.isNaN(date.getTime())) return null;
  return date;
}

/**
 * 信息流 / 个人页卡片时间：
 * - 1 小时内：x分钟前
 * - 当天更早：x小时前
 * - 昨天：昨天 HH:mm
 * - 一周内：x天 HH:mm
 * - 其余：yyyy-MM-dd HH:mm
 */
export function formatCardTime(value?: string | null): string {
  if (!value?.trim()) return '';

  const raw = value.trim();
  const date = parseDateTime(raw);
  if (!date) return raw;

  const now = new Date();
  if (date.getTime() > now.getTime()) {
    return formatAbsolute(date);
  }

  const diffMs = now.getTime() - date.getTime();
  const diffMin = Math.floor(diffMs / 60_000);
  const diffHour = Math.floor(diffMs / 3_600_000);
  const timePart = `${pad2(date.getHours())}:${pad2(date.getMinutes())}`;
  const dayDiff = calendarDayDiff(date, now);

  if (dayDiff === 0) {
    if (diffMin < 1) return '刚刚';
    if (diffMin < 60) return `${diffMin}分钟前`;
    return `${diffHour}小时前`;
  }

  if (dayDiff === 1) {
    return `昨天 ${timePart}`;
  }

  if (dayDiff >= 2 && dayDiff < 7) {
    return `${dayDiff}天前`;
  }

  return formatAbsolute(date);
}

/** 详情页等固定格式：yyyy-MM-dd HH:mm（不做相对时间） */
export function formatDateTime(value?: string | null): string {
  if (!value?.trim()) return '';
  const date = parseDateTime(value);
  if (!date) {
    return value!
      .trim()
      .replace('T', ' ')
      .replace(/\.\d{3}Z?$/i, '')
      .slice(0, 16);
  }
  return formatAbsolute(date);
}
