export const REPORT_CATEGORIES = [
  { value: 'spam', label: '垃圾广告' },
  { value: 'illegal', label: '违法违规' },
  { value: 'porn', label: '色情低俗' },
  { value: 'abuse', label: '人身攻击' },
  { value: 'infringement', label: '侵权抄袭' },
  { value: 'other', label: '其他' },
] as const;

export type ReportCategoryValue = (typeof REPORT_CATEGORIES)[number]['value'];

export function buildReportReason(
  categoryLabel: string,
  detail?: string,
): string {
  const trimmed = detail?.trim();
  return trimmed ? `[${categoryLabel}] ${trimmed}` : `[${categoryLabel}]`;
}
