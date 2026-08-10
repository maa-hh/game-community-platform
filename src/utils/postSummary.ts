/** 卡片摘要：优先用户填写摘要，否则回退正文 */
export function normalizePostText(text?: string | null): string {
  if (!text?.trim()) return '';
  return text.trim().replace(/\s+/g, ' ');
}

export function resolvePostCardSummary(input: {
  summary?: string | null;
  body?: string | null;
}): string {
  const userSummary = normalizePostText(input.summary);
  if (userSummary) return userSummary;
  return normalizePostText(input.body);
}
