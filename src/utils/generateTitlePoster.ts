import { BRAND_NAME } from '@/constants/brand';
import {
  FEED_CARD_POSTER_CANVAS_WIDTH,
  resolveFeedPosterMaxHeight,
  resolveFeedPosterMinHeight,
} from '@/constants/feedCardMedia';

const DEFAULT_POSTER_WIDTH = FEED_CARD_POSTER_CANVAS_WIDTH;
const BRAND_BAR_HEIGHT_BASE = 32;
const CONTENT_VERTICAL_PAD_BASE = 40;
const TOP_CONTENT_PAD_BASE = 20;
const MIN_FONT_SIZE = 14;
const IDEAL_FONT_SIZE = 40;
const UNLIMITED_LINES = 999;

const BRAND_PRIMARY = '#ff6600';
const BRAND_PRIMARY_DARK = '#d94e00';
const BRAND_PRIMARY_LIGHT = '#ff9a4d';

const FONT_FAMILY =
  '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "PingFang SC", "Microsoft YaHei", sans-serif';

export interface TitlePosterSize {
  width: number;
  height: number;
}

export interface TitlePosterLayout extends TitlePosterSize {
  fontSize: number;
  lines: string[];
  lineHeight: number;
}

function resolvePaddingX(width: number): number {
  return Math.max(4, Math.round((width * 36) / DEFAULT_POSTER_WIDTH));
}

function resolveContentVerticalPad(width: number): number {
  return Math.max(
    6,
    Math.round((CONTENT_VERTICAL_PAD_BASE * width) / DEFAULT_POSTER_WIDTH),
  );
}

function resolveBrandBarHeight(width: number): number {
  return Math.max(
    8,
    Math.round((BRAND_BAR_HEIGHT_BASE * width) / DEFAULT_POSTER_WIDTH),
  );
}

function resolveTopContentPad(width: number): number {
  return Math.max(
    4,
    Math.round((TOP_CONTENT_PAD_BASE * width) / DEFAULT_POSTER_WIDTH),
  );
}

function resolveBrandFontSize(width: number): number {
  return Math.max(8, Math.round((16 * width) / DEFAULT_POSTER_WIDTH));
}

/** 首选字号：仅随画布宽度缩放，不因标题长短预先缩小 */
function resolveIdealFontSize(posterWidth: number): number {
  const scale = Math.min(1, posterWidth / DEFAULT_POSTER_WIDTH);
  return Math.max(8, Math.round(IDEAL_FONT_SIZE * scale));
}

function wrapText(
  ctx: CanvasRenderingContext2D,
  text: string,
  maxWidth: number,
  maxLines: number,
) {
  const chars = Array.from(text);
  const lines: string[] = [];
  let line = '';

  chars.forEach((char) => {
    const next = line + char;
    if (ctx.measureText(next).width > maxWidth && line) {
      lines.push(line);
      line = char;
    } else {
      line = next;
    }
  });

  if (line) lines.push(line);
  if (maxLines >= UNLIMITED_LINES) return lines;
  return lines.slice(0, maxLines);
}

function measureLinesHeight(lineCount: number, lineHeight: number): number {
  return lineCount * lineHeight;
}

function measureTitleBlock(
  ctx: CanvasRenderingContext2D,
  title: string,
  posterWidth: number,
  fontSize: number,
  maxLines: number,
) {
  const paddingX = resolvePaddingX(posterWidth);
  const topPad = resolveTopContentPad(posterWidth);
  const bottomPad = resolveContentVerticalPad(posterWidth);
  const brandBarHeight = resolveBrandBarHeight(posterWidth);
  const maxTextWidth = posterWidth - paddingX * 2;
  ctx.font = `700 ${fontSize}px ${FONT_FAMILY}`;
  const lines = wrapText(ctx, title, maxTextWidth, maxLines);
  const lineHeight = fontSize * 1.35;
  const contentHeight = measureLinesHeight(lines.length, lineHeight);
  const totalHeight = Math.round(
    topPad + contentHeight + bottomPad + brandBarHeight,
  );
  return { lines, lineHeight, contentHeight, totalHeight };
}

function shrinkFontToFitMaxHeight(
  ctx: CanvasRenderingContext2D,
  title: string,
  posterWidth: number,
  maxHeight: number,
  startFontSize: number,
) {
  const topPad = resolveTopContentPad(posterWidth);
  const bottomPad = resolveContentVerticalPad(posterWidth);
  const brandBarHeight = resolveBrandBarHeight(posterWidth);
  const maxContentHeight = maxHeight - brandBarHeight - bottomPad - topPad;
  let fontSize = startFontSize;
  let result = measureTitleBlock(
    ctx,
    title,
    posterWidth,
    fontSize,
    UNLIMITED_LINES,
  );

  for (let attempt = 0; attempt < 16; attempt += 1) {
    const maxLines = Math.max(
      1,
      Math.floor(maxContentHeight / (fontSize * 1.35)),
    );
    result = measureTitleBlock(ctx, title, posterWidth, fontSize, maxLines);
    if (result.contentHeight <= maxContentHeight) {
      return { fontSize, ...result };
    }
    fontSize -= 2;
    if (fontSize < MIN_FONT_SIZE) {
      result = measureTitleBlock(
        ctx,
        title,
        posterWidth,
        MIN_FONT_SIZE,
        maxLines,
      );
      return { fontSize: MIN_FONT_SIZE, ...result };
    }
  }

  return { fontSize: MIN_FONT_SIZE, ...result };
}

/** 瀑布流标题海报：高度在 2:1 ~ 3:4 间自适应；仅 3:4 仍放不下时才缩字号 */
export function resolveFeedTitlePosterSize(
  width: number,
  title: string,
): TitlePosterSize {
  const trimmed = title.trim();
  const posterWidth = Math.max(1, Math.round(width));
  const minHeight = resolveFeedPosterMinHeight(posterWidth);
  const maxHeight = resolveFeedPosterMaxHeight(posterWidth);

  if (!trimmed) {
    return { width: posterWidth, height: minHeight };
  }

  const canvas = document.createElement('canvas');
  const ctx = canvas.getContext('2d');
  if (!ctx) {
    return { width: posterWidth, height: minHeight };
  }

  const idealFontSize = resolveIdealFontSize(posterWidth);
  const natural = measureTitleBlock(
    ctx,
    trimmed,
    posterWidth,
    idealFontSize,
    UNLIMITED_LINES,
  );

  if (natural.totalHeight <= maxHeight) {
    return {
      width: posterWidth,
      height: Math.max(minHeight, natural.totalHeight),
    };
  }

  return { width: posterWidth, height: maxHeight };
}

export function resolveDefaultTitlePosterSize(title: string): TitlePosterSize {
  return resolveFeedTitlePosterSize(DEFAULT_POSTER_WIDTH, title);
}

export function measureTitlePosterLayout(
  title: string,
  size: TitlePosterSize,
): TitlePosterLayout {
  const trimmed = title.trim();
  if (!trimmed) {
    throw new Error('标题不能为空');
  }

  const canvas = document.createElement('canvas');
  const ctx = canvas.getContext('2d');
  if (!ctx) {
    throw new Error('无法创建画布');
  }

  const maxHeightAtWidth = resolveFeedPosterMaxHeight(size.width);
  const idealFontSize = resolveIdealFontSize(size.width);
  const isAtMaxAspect = size.height >= maxHeightAtWidth - 1;

  if (!isAtMaxAspect) {
    const natural = measureTitleBlock(
      ctx,
      trimmed,
      size.width,
      idealFontSize,
      UNLIMITED_LINES,
    );
    return {
      width: size.width,
      height: size.height,
      fontSize: idealFontSize,
      lines: natural.lines,
      lineHeight: natural.lineHeight,
    };
  }

  const fitted = shrinkFontToFitMaxHeight(
    ctx,
    trimmed,
    size.width,
    size.height,
    idealFontSize,
  );

  return {
    width: size.width,
    height: size.height,
    fontSize: fitted.fontSize,
    lines: fitted.lines,
    lineHeight: fitted.lineHeight,
  };
}

function drawDecorations(
  ctx: CanvasRenderingContext2D,
  width: number,
  height: number,
) {
  const circles = [
    { x: -40, y: height * 0.18, r: width * 0.28, alpha: 0.1 },
    { x: width + 60, y: height * 0.32, r: width * 0.32, alpha: 0.08 },
    { x: width * 0.8, y: height + 20, r: width * 0.24, alpha: 0.1 },
  ];

  circles.forEach(({ x, y, r, alpha }) => {
    ctx.beginPath();
    ctx.arc(x, y, r, 0, Math.PI * 2);
    ctx.fillStyle = `rgba(255, 255, 255, ${alpha})`;
    ctx.fill();
  });
}

function drawBackground(
  ctx: CanvasRenderingContext2D,
  width: number,
  height: number,
) {
  const brandBarHeight = resolveBrandBarHeight(width);
  const gradient = ctx.createLinearGradient(0, 0, width, height);
  gradient.addColorStop(0, BRAND_PRIMARY_DARK);
  gradient.addColorStop(0.45, BRAND_PRIMARY);
  gradient.addColorStop(1, BRAND_PRIMARY_LIGHT);
  ctx.fillStyle = gradient;
  ctx.fillRect(0, 0, width, height);

  drawDecorations(ctx, width, height);

  const inset = Math.max(12, Math.round(width * 0.03));
  ctx.strokeStyle = 'rgba(255, 255, 255, 0.24)';
  ctx.lineWidth = 3;
  ctx.strokeRect(inset, inset, width - inset * 2, height - inset * 2);

  ctx.fillStyle = 'rgba(0, 0, 0, 0.16)';
  ctx.fillRect(0, height - brandBarHeight, width, brandBarHeight);
}

function drawBrandBar(
  ctx: CanvasRenderingContext2D,
  width: number,
  height: number,
) {
  const brandBarHeight = resolveBrandBarHeight(width);
  const fontSize = resolveBrandFontSize(width);
  ctx.fillStyle = '#ffffff';
  ctx.font = `600 ${fontSize}px ${FONT_FAMILY}`;
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillText(BRAND_NAME, width / 2, height - brandBarHeight / 2);
}

function drawTitle(ctx: CanvasRenderingContext2D, layout: TitlePosterLayout) {
  const { width, height, fontSize, lines, lineHeight } = layout;
  const topPad = resolveTopContentPad(width);
  const bottomPad = resolveContentVerticalPad(width);
  const brandBarHeight = resolveBrandBarHeight(width);
  const contentAreaHeight = height - brandBarHeight - bottomPad - topPad;
  const blockHeight = measureLinesHeight(lines.length, lineHeight);
  let y = topPad + (contentAreaHeight - blockHeight) / 2 + lineHeight / 2;

  ctx.font = `700 ${fontSize}px ${FONT_FAMILY}`;
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.shadowColor = 'rgba(0, 0, 0, 0.22)';
  ctx.shadowBlur = 10;
  ctx.shadowOffsetY = 2;
  ctx.fillStyle = '#ffffff';

  lines.forEach((line) => {
    ctx.fillText(line, width / 2, y);
    y += lineHeight;
  });

  ctx.shadowColor = 'transparent';
  ctx.shadowBlur = 0;
  ctx.shadowOffsetY = 0;
}

/** 将标题渲染为固定尺寸封面图（仅展示侧动态生成，不上传） */
export async function generateTitlePosterBlob(
  title: string,
  size?: TitlePosterSize,
): Promise<Blob> {
  const layout = measureTitlePosterLayout(
    title,
    size ?? resolveDefaultTitlePosterSize(title),
  );

  const canvas = document.createElement('canvas');
  canvas.width = layout.width;
  canvas.height = layout.height;
  const ctx = canvas.getContext('2d');
  if (!ctx) {
    throw new Error('无法创建画布');
  }

  drawBackground(ctx, layout.width, layout.height);
  drawTitle(ctx, layout);
  drawBrandBar(ctx, layout.width, layout.height);

  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => {
        if (blob) resolve(blob);
        else reject(new Error('导出封面失败'));
      },
      'image/png',
      0.92,
    );
  });
}
