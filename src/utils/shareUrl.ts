import { BRAND_NAME } from '@/constants/brand';
import { BASE_URL } from '@/service/config';

/** OG 分享落地页根地址（网关），默认同 REACT_APP_BASE_URL */
function shareBaseUrl(): string {
  const currentOrigin =
    typeof window !== 'undefined' && window.location.origin
      ? window.location.origin
      : '';
  const raw =
    process.env.REACT_APP_SHARE_BASE_URL?.trim() ||
    BASE_URL.trim() ||
    currentOrigin;
  return raw.replace(/\/$/, '');
}

/**
 * 外链分享地址：微信 / QQ 爬虫抓取 OG meta 后展示卡片，用户点击再进详情
 * 必须指向网关 `/share/post/{id}`，不能是前端 `/post/{id}` SPA 路由
 */
export function buildSharePostUrl(articleId: string | number): string {
  return `${shareBaseUrl()}/share/post/${articleId}`;
}

/** 游戏详情页 SPA 地址（复制分享用） */
export function buildGameDetailPageUrl(appId: string | number): string {
  const origin =
    typeof window !== 'undefined' && window.location.origin
      ? window.location.origin
      : shareBaseUrl();
  return `${origin.replace(/\/$/, '')}/game/${appId}`;
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
