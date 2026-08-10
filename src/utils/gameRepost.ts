import type { ContentCardPostType } from '@/types/content';
import type { PostRefCard } from '@/types/post';
import { mapGameTagsFromRaw, type IGameTagRaw } from '@/utils/mapGameTag';
import { mapNumericPostType } from '@/utils/postType';
import { resolveGameCoverUrl } from '@/utils/steamImage';

export const GAME_REPOST_SNAPSHOT_KEY = '__game_repost__';
export const GAME_SHARE_TITLE_PREFIX = '分享了';
/** 旧版游戏分享标题前缀（仅精确匹配游戏名时视为分享） */
export const GAME_REPOST_TITLE_PREFIX = '转发：';

const GAME_SHARE_MARKER_RE = /\[\[game-share:(\d+)\]\]\s*$/;

function normalizeAppId(value: unknown): number | undefined {
  const appId = Number(value);
  return Number.isFinite(appId) && appId > 0 ? appId : undefined;
}

export interface GameRepostSnapshot {
  appId: number;
  title: string;
  summary?: string;
  coverUrl?: string;
}

export interface GameRepostDetectInput {
  postType?: number;
  refArticleId?: string;
  title?: string;
  summary?: string;
  content?: string;
  gameTags?: IGameTagRaw[];
  contentParagraphs?: Record<string, string> | null;
}

export function appendGameShareMarker(content: string, appId: number): string {
  const base = stripGameShareMarker(content);
  return `${base}\n[[game-share:${appId}]]`;
}

export function stripGameShareMarker(content?: string | null): string {
  if (!content?.trim()) return '';
  return content.replace(/\s*\[\[game-share:\d+\]\]\s*$/, '').trimEnd();
}

export function parseGameShareMarkerAppId(
  content?: string | null,
): number | undefined {
  if (!content) return undefined;
  const match = content.match(GAME_SHARE_MARKER_RE);
  if (!match) return undefined;
  const appId = Number(match[1]);
  return Number.isFinite(appId) && appId > 0 ? appId : undefined;
}

/** 判断文本是否为游戏分享元数据 JSON（不应展示给用户） */
export function isGameRepostSnapshotJson(text?: string | null): boolean {
  const trimmed = text?.trim();
  if (!trimmed?.startsWith('{')) return false;
  try {
    const parsed = JSON.parse(trimmed) as GameRepostSnapshot;
    return Boolean(parsed?.appId && parsed?.title?.trim());
  } catch {
    return false;
  }
}

function stripSnapshotFromText(text?: string | null): string {
  if (!text?.trim()) return '';
  const lines = text
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line && !isGameRepostSnapshotJson(line));
  return stripGameShareMarker(lines.join('\n').trim());
}

export function sanitizeGameShareContent(input: {
  content?: string;
  summary?: string;
  contentParagraphs?: Record<string, string> | null;
}): string {
  const fromContent = stripSnapshotFromText(input.content);
  if (fromContent) return fromContent;

  const fromSummary = stripSnapshotFromText(input.summary);
  if (fromSummary) return fromSummary;

  return '';
}

export function buildGameRepostSnapshot(
  snapshot: GameRepostSnapshot,
): Record<string, string> {
  return {
    [GAME_REPOST_SNAPSHOT_KEY]: JSON.stringify(snapshot),
  };
}

export function parseGameRepostSnapshot(
  contentParagraphs?: Record<string, string> | null,
): GameRepostSnapshot | undefined {
  const raw = contentParagraphs?.[GAME_REPOST_SNAPSHOT_KEY];
  if (!raw?.trim()) return undefined;
  try {
    const parsed = JSON.parse(raw) as GameRepostSnapshot;
    if (!parsed?.appId || !parsed.title?.trim()) return undefined;
    return parsed;
  } catch {
    return undefined;
  }
}

function isLegacyGameShareTitle(
  title: string,
  gameTags?: IGameTagRaw[],
): boolean {
  if (!title.startsWith(GAME_REPOST_TITLE_PREFIX)) return false;
  const suffix = title.slice(GAME_REPOST_TITLE_PREFIX.length).trim();
  if (!suffix) return false;
  return (
    gameTags?.some((tag) => {
      const name = tag.name?.trim();
      return Boolean(name && suffix === name);
    }) ?? false
  );
}

/** 游戏分享动态（与普通「带游戏标签」帖子区分） */
export function isGameRepostArticle(input: GameRepostDetectInput): boolean {
  if (input.refArticleId) return false;
  if (parseGameRepostSnapshot(input.contentParagraphs)) return true;

  const markerAppId =
    parseGameShareMarkerAppId(input.content) ??
    parseGameShareMarkerAppId(input.summary);
  if (markerAppId) return true;

  const title = input.title?.trim() || '';
  if (title.startsWith(GAME_SHARE_TITLE_PREFIX)) return true;

  if (!input.gameTags?.length) return false;
  if (isLegacyGameShareTitle(title, input.gameTags)) return true;

  return false;
}

export function resolveDisplayPostType(
  input: GameRepostDetectInput,
): ContentCardPostType {
  if (isGameRepostArticle(input)) return 'repost';
  return mapNumericPostType(input.postType);
}

/** 从帖子字段解析关联的游戏 appId */
export function resolveGameRepostAppId(
  input: GameRepostDetectInput,
): number | undefined {
  const snapshot = parseGameRepostSnapshot(input.contentParagraphs);
  if (snapshot?.appId) return normalizeAppId(snapshot.appId);

  const markerAppId =
    parseGameShareMarkerAppId(input.content) ??
    parseGameShareMarkerAppId(input.summary);
  if (markerAppId) return normalizeAppId(markerAppId);

  for (const tag of input.gameTags ?? []) {
    const appId = normalizeAppId(tag.appId);
    if (appId) return appId;
  }
  return undefined;
}

/** 列表映射：为游戏分享帖构建引用卡（含封面） */
export function buildGameRepostRefPostForArticle(
  article: GameRepostDetectInput & { gameTags?: IGameTagRaw[] },
  meta?: { name?: string; summary?: string; coverUrl?: string },
): PostRefCard | undefined {
  return buildGameRepostRefPost({
    gameTags: article.gameTags,
    contentParagraphs: article.contentParagraphs,
    content: article.content,
    summary: article.summary,
    meta,
  });
}

export function buildGameRepostRefPost(input: {
  gameTags?: IGameTagRaw[];
  contentParagraphs?: Record<string, string> | null;
  content?: string;
  summary?: string;
  meta?: { name?: string; summary?: string; coverUrl?: string };
}): PostRefCard | undefined {
  const snapshot = parseGameRepostSnapshot(input.contentParagraphs);
  const gameTags = mapGameTagsFromRaw(input.gameTags) ?? [];
  const markerAppId =
    parseGameShareMarkerAppId(input.content) ??
    parseGameShareMarkerAppId(input.summary);
  const appId = normalizeAppId(
    snapshot?.appId ?? markerAppId ?? gameTags[0]?.appId,
  );
  if (!appId) return undefined;

  const tag =
    gameTags.find((item) => normalizeAppId(item.appId) === appId) ??
    gameTags[0];

  return {
    id: `game-${appId}`,
    title:
      snapshot?.title?.trim() ||
      tag?.name ||
      input.meta?.name?.trim() ||
      `游戏 ${appId}`,
    summary:
      snapshot?.summary?.trim() || input.meta?.summary?.trim() || undefined,
    coverUrl: resolveGameCoverUrl(
      appId,
      snapshot?.coverUrl || input.meta?.coverUrl || tag?.iconUrl,
    ),
    postType: 'image_text',
    author: { accountId: 0, nickname: '游戏' },
  };
}

export function isGameRefPostId(id?: string): boolean {
  return Boolean(id?.startsWith('game-'));
}

export function resolveGameRefAppId(id?: string): number | undefined {
  if (!isGameRefPostId(id)) return undefined;
  const appId = Number(id!.slice(5));
  return Number.isFinite(appId) && appId > 0 ? appId : undefined;
}
