/** 有效字符数：空格、换行等空白不计 */
export function countMeaningfulChars(value?: string | null): number {
  if (!value) return 0;
  return value.replace(/\s/g, '').length;
}

/** 按有效字符上限裁剪，保留空白字符 */
export function clipByMeaningfulChars(value: string, max: number): string {
  let count = 0;
  let result = '';
  for (const ch of value) {
    if (/\s/.test(ch)) {
      result += ch;
      continue;
    }
    if (count >= max) continue;
    result += ch;
    count += 1;
  }
  return result;
}
