import { BRAND_NAME } from '@/constants/brand';
import { BASE_URL } from '@/service/config';

/** 用户可直接打开的前端站点根地址。 */
function frontendBaseUrl(): string {
  const currentOrigin =
    typeof window !== 'undefined' && window.location.origin
      ? window.location.origin
      : '';
  const raw = currentOrigin || BASE_URL.trim();
  return raw.replace(/\/$/, '');
}

/**
 * 帖子分享地址必须指向已注册的前端路由。
 * `/share/post/{id}` 只有反向代理显式转发到后端时才可用，不能作为默认地址。
 */
export function buildSharePostUrl(articleId: string | number): string {
  return `${frontendBaseUrl()}/post/${articleId}`;
}

/** 游戏详情页 SPA 地址（复制分享用） */
export function buildGameDetailPageUrl(appId: string | number): string {
  return `${frontendBaseUrl()}/game/${appId}`;
}

/** 复制游戏详情链接时的分享文案 */
export function buildShareGameCopyText(options: {
  name: string;
  summary?: string;
  url: string;
}): string {
  const summary = options.summary?.trim();
  return [
    `【${options.name}】`,
    summary || undefined,
    '点击查看游戏详情 👇',
    options.url,
    `—— 来自${BRAND_NAME}`,
  ]
    .filter((line): line is string => Boolean(line))
    .join('\n');
}

/** 复制到剪贴板的分享文案（标题 + 摘要 + 引导语 + 链接） */
export function buildShareCopyText(options: {
  title: string;
  summary?: string;
  url: string;
}): string {
  const summary = options.summary?.trim();
  return [
    `【${options.title}】`,
    summary || undefined,
    '点击查看帖子详情 👇',
    options.url,
    `—— 来自${BRAND_NAME}`,
  ]
    .filter((line): line is string => Boolean(line))
    .join('\n');
}
