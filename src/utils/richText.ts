const BLOCK_TAGS = new Set([
  'ADDRESS',
  'ARTICLE',
  'ASIDE',
  'BLOCKQUOTE',
  'DIV',
  'H1',
  'H2',
  'H3',
  'H4',
  'H5',
  'H6',
  'LI',
  'OL',
  'P',
  'PRE',
  'SECTION',
  'UL',
]);

const HTML_TAG_RE = /<[^>]*>/g;

/** 将 Quill HTML 转成后端用于摘要、搜索和审核的纯文本。 */
export function richHtmlToPlainText(html: string): string {
  if (!html?.trim()) return '';

  if (typeof DOMParser === 'undefined') {
    return html
      .replace(/<br\s*\/?>/gi, '\n')
      .replace(HTML_TAG_RE, '')
      .replace(/\u00a0/g, ' ')
      .replace(/[ \t]+/g, ' ')
      .trim();
  }

  const doc = new DOMParser().parseFromString(html, 'text/html');
  const lines: string[] = [];

  const visit = (node: Node, line: string) => {
    if (node.nodeType === Node.TEXT_NODE) {
      return `${line}${node.textContent || ''}`;
    }

    if (node.nodeType !== Node.ELEMENT_NODE) return line;
    const element = node as HTMLElement;
    let next = line;
    element.childNodes.forEach((child) => {
      next = visit(child, next);
    });

    if (element.tagName === 'BR' || BLOCK_TAGS.has(element.tagName)) {
      const normalized = next.replace(/[ \t\u00a0]+/g, ' ').trim();
      if (normalized) lines.push(normalized);
      return '';
    }
    return next;
  };

  const remainder = visit(doc.body, '');
  const normalizedRemainder = remainder.replace(/[ \t\u00a0]+/g, ' ').trim();
  if (normalizedRemainder) lines.push(normalizedRemainder);

  return lines
    .join('\n')
    .replace(/\n{2,}/g, '\n')
    .trim();
}

/** 把纯文本正文拆成后端兼容的有序段落。 */
export function richHtmlToParagraphs(html: string): Record<string, string> {
  const plainText = richHtmlToPlainText(html);
  return plainText
    .split(/\n+/)
    .map((value) => value.trim())
    .filter(Boolean)
    .reduce<Record<string, string>>((result, value, index) => {
      result[`p${index + 1}`] = value;
      return result;
    }, {});
}
