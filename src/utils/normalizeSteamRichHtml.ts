/** 游戏介绍 / 配置需求等 Steam 富文本展示归一化 */
export function normalizeSteamRichHtml(html: string): string {
  if (!html.trim()) return '';

  return html
    .replace(/<p>(?:\s|&nbsp;|<br\s*\/?>)*<\/p>/gi, '')
    .replace(/(?:<br\s*\/?>\s*)+(?=<img\b)/gi, '')
    .replace(/(<img\b[^>]*>)\s*(?:<br\s*\/?>\s*)+/gi, '$1')
    .replace(/(?:<br\s*\/?>\s*){3,}/gi, '<br /><br />')
    .replace(/\sstyle="([^"]*)"/gi, (_, styles: string) => {
      const cleaned = styles
        .replace(/(?:^|;)\s*color\s*:\s*[^;]+;?/gi, '')
        .replace(/;;+/g, ';')
        .replace(/^;|;$/g, '')
        .trim();
      return cleaned ? ` style="${cleaned}"` : '';
    });
}
