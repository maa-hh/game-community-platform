export const BODY_IMAGE_MARKER_SPLIT_RE =
  /(\[图片:\d+(?::w=\d+)?(?::h=\d+)?\])/g;

export const BODY_IMAGE_MARKER_TEST_RE = /\[图片:\d+(?::w=\d+)?(?::h=\d+)?\]/;

export interface IImageMarkerMeta {
  index: number;
  widthPercent: number;
  maxHeight?: number;
}

export function parseImageMarker(part: string): IImageMarkerMeta | null {
  const match = part.match(/^\[图片:(\d+)(?::w=(\d+))?(?::h=(\d+))?\]$/);
  if (!match) return null;
  return {
    index: Number(match[1]) - 1,
    widthPercent: match[2] ? Number(match[2]) : 100,
    maxHeight: match[3] ? Number(match[3]) : undefined,
  };
}

export function formatImageMarker(
  index: number,
  widthPercent = 100,
  maxHeight?: number,
): string {
  let extra = '';
  if (widthPercent > 0 && widthPercent !== 100) {
    extra += `:w=${Math.round(widthPercent)}`;
  }
  if (maxHeight && maxHeight > 0) {
    extra += `:h=${Math.round(maxHeight)}`;
  }
  return `[图片:${index}${extra}]`;
}

export function countBodyImageMarkers(content: string): number {
  return (content.match(/\[图片:\d+(?::w=\d+)?(?::h=\d+)?\]/g) || []).length;
}

/** 编辑旧帖时去掉正文内嵌图标记，仅保留纯文字 */
export function stripBodyImageMarkers(content: string): string {
  return content
    .replace(BODY_IMAGE_MARKER_SPLIT_RE, '')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
}
