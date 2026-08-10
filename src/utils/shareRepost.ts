export const REPOST_TITLE_MAX = 80;
export const REPOST_BODY_MAX = 500;

export function buildDefaultRepostTitle(refTitle: string): string {
  const name = refTitle.trim() || '这条动态';
  return `转发了${name}`.slice(0, REPOST_TITLE_MAX);
}

export function buildDefaultRepostContent(refTitle: string): string {
  const name = refTitle.trim() || '这条动态';
  return `转发了${name}`.slice(0, REPOST_BODY_MAX);
}

export function buildDefaultGameShareTitle(gameName: string): string {
  const name = gameName.trim() || '这款游戏';
  return `分享了${name}`.slice(0, REPOST_TITLE_MAX);
}

export function buildDefaultGameShareContent(gameName: string): string {
  const name = gameName.trim() || '这款游戏';
  return `分享了${name}`.slice(0, REPOST_BODY_MAX);
}

export function resolveShareTitle(
  custom: string | undefined,
  fallback: string,
): string {
  return custom?.trim() || fallback;
}

export function resolveShareContent(
  custom: string | undefined,
  fallback: string,
): string {
  return custom?.trim() || fallback;
}
