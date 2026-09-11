import dayjs, { type Dayjs } from 'dayjs';
import isoWeek from 'dayjs/plugin/isoWeek';

import type { HotRankBoard } from '@/service/hotRank';

dayjs.extend(isoWeek);

/** 与后端 HotRankPeriodUtils（ISO 自然周，周一至周日）对齐 */
export function formatWeeklyPeriodKey(date: Dayjs): string {
  const normalized = date.startOf('isoWeek');
  const year = normalized.isoWeekYear();
  const week = normalized.isoWeek();
  return `${year}-W${String(week).padStart(2, '0')}`;
}

export function formatPeriodKey(board: HotRankBoard, date: Dayjs): string {
  if (board === 'weekly') {
    return formatWeeklyPeriodKey(date);
  }
  return date.format('YYYY-MM-DD');
}

/**
 * 解析 URL 中的榜单周期键。
 * 周榜使用后端约定的 ISO 周格式（YYYY-Www），不是 Dayjs 默认可解析的日期格式。
 */
export function parsePeriodKey(
  board: HotRankBoard,
  periodKey: string | null,
): Dayjs | null {
  if (!periodKey) return null;

  if (board === 'weekly') {
    const match = /^(\d{4})-W(\d{2})$/.exec(periodKey);
    if (!match) return null;

    const year = Number(match[1]);
    const week = Number(match[2]);
    if (week < 1 || week > 53) return null;

    // ISO 第 1 周一定包含 1 月 4 日，以此计算目标周的周一。
    const parsed = dayjs(`${year}-01-04`)
      .startOf('isoWeek')
      .add(week - 1, 'week');
    return formatWeeklyPeriodKey(parsed) === periodKey ? parsed : null;
  }

  const parsed = dayjs(periodKey);
  return parsed.isValid() && parsed.format('YYYY-MM-DD') === periodKey
    ? parsed.startOf('day')
    : null;
}

/** 周榜默认：上一完整自然周（上周一） */
export function defaultWeeklyPeriodDate(): Dayjs {
  return dayjs().startOf('isoWeek').subtract(1, 'week');
}

export function defaultPeriodDate(board: HotRankBoard): Dayjs {
  if (board === 'weekly') {
    return defaultWeeklyPeriodDate();
  }
  return dayjs();
}

export function normalizePeriodDate(board: HotRankBoard, value: Dayjs): Dayjs {
  if (board === 'weekly') {
    return value.startOf('isoWeek');
  }
  return value.startOf('day');
}

export function isCurrentIsoWeek(date: Dayjs): boolean {
  return formatWeeklyPeriodKey(date) === formatWeeklyPeriodKey(dayjs());
}

/** 日榜/总榜周期展示：今天、昨天、N天前，其余 YYYY-MM-DD */
export function formatDailyPeriodLabel(
  date: Dayjs,
  now: Dayjs = dayjs(),
): string {
  const target = date.startOf('day');
  const today = now.startOf('day');
  const dayDiff = today.diff(target, 'day');

  if (dayDiff < 0) {
    return target.format('YYYY-MM-DD');
  }
  if (dayDiff === 0) return '今天';
  if (dayDiff === 1) return '昨天';
  if (dayDiff >= 2 && dayDiff < 7) return `${dayDiff}天前`;
  return target.format('YYYY-MM-DD');
}

/** 周榜周期展示：本周、上周，其余具体日期区间 */
export function formatWeeklyPeriodLabel(
  date: Dayjs,
  now: Dayjs = dayjs(),
): string {
  const normalized = date.startOf('isoWeek');
  const targetKey = formatWeeklyPeriodKey(normalized);
  const thisWeekKey = formatWeeklyPeriodKey(now);
  const lastWeekKey = formatWeeklyPeriodKey(now.subtract(1, 'week'));

  if (targetKey === thisWeekKey) return '本周';
  if (targetKey === lastWeekKey) return '上周';

  const end = normalized.endOf('isoWeek');
  return `${normalized.format('YYYY-MM-DD')} ~ ${end.format('MM-DD')}`;
}

/** 热榜 DatePicker 展示（与 formatCardTime 相对时间口径一致） */
export function formatHotRankPeriodLabel(
  board: HotRankBoard,
  date: Dayjs,
  now: Dayjs = dayjs(),
): string {
  if (board === 'weekly') {
    return formatWeeklyPeriodLabel(date, now);
  }
  return formatDailyPeriodLabel(date, now);
}
