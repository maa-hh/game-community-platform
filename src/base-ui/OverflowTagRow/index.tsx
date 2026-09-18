import React, {
  memo,
  useCallback,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import type { CSSProperties, FC, MouseEventHandler, ReactNode } from 'react';
import { Link } from 'react-router-dom';

import './style.less';

export type OverflowTagRowVariant = 'default' | 'game' | 'repost';

export interface OverflowTagRowItem {
  key: string;
  text: string;
  icon?: string;
  variant?: OverflowTagRowVariant;
  to?: string;
  state?: unknown;
  leading?: ReactNode;
  onClick?: MouseEventHandler<HTMLElement>;
  onIntent?: () => void;
}

export interface OverflowTagRowProps {
  items: OverflowTagRowItem[];
  className?: string;
  /** 与 feed-masonry-card / post-display-tags / game-card 等现有样式对齐 */
  preset?: 'feed' | 'post' | 'game';
}

interface TagRowLayout {
  fullCount: number;
  showPartial: boolean;
  showOverflow: boolean;
  partialMaxWidth?: number;
}

const PRESET_CLASS: Record<
  NonNullable<OverflowTagRowProps['preset']>,
  {
    tag: string;
    tagText: string;
    tagIcon: string;
    tagGame: string;
    tagRepost: string;
    overflow: string;
  }
> = {
  feed: {
    tag: 'feed-masonry-card__tag',
    tagText: 'feed-masonry-card__tag-text',
    tagIcon: 'feed-masonry-card__tag-icon',
    tagGame: 'feed-masonry-card__tag--game',
    tagRepost: 'feed-masonry-card__tag--repost',
    overflow: 'feed-masonry-card__tag feed-masonry-card__tag--overflow',
  },
  post: {
    tag: 'post-display-tags__tag',
    tagText: 'post-display-tags__tag-text',
    tagIcon: 'post-display-tags__tag-icon',
    tagGame: 'post-display-tags__tag--game',
    tagRepost: 'post-display-tags__tag--repost',
    overflow: 'post-display-tags__tag post-display-tags__tag--overflow',
  },
  game: {
    tag: 'game-card__tag',
    tagText: 'game-card__tag-text',
    tagIcon: 'game-card__tag-icon',
    tagGame: 'game-card__tag--game',
    tagRepost: 'game-card__tag--repost',
    overflow: 'game-card__tag game-card__tag--overflow',
  },
};

function resolveTagClass(
  preset: OverflowTagRowProps['preset'],
  variant: OverflowTagRowVariant = 'default',
  mode: 'full' | 'partial' = 'full',
): string {
  const classes = PRESET_CLASS[preset ?? 'feed'];
  const base =
    variant === 'game'
      ? `${classes.tag} ${classes.tagGame}`
      : variant === 'repost'
        ? `${classes.tag} ${classes.tagRepost}`
        : classes.tag;
  if (mode === 'partial') return `${base} overflow-tag-row__tag--partial`;
  return `${base} overflow-tag-row__tag--full`;
}

interface TagChipProps {
  item: OverflowTagRowItem;
  preset: OverflowTagRowProps['preset'];
  mode?: 'full' | 'partial';
  style?: CSSProperties;
  measure?: boolean;
  onIconLoad?: () => void;
}

const TagChip: FC<TagChipProps> = ({
  item,
  preset,
  mode = 'full',
  style,
  measure = false,
  onIconLoad,
}) => {
  const classes = PRESET_CLASS[preset ?? 'feed'];
  const className = resolveTagClass(preset, item.variant, mode);
  const content = (
    <>
      {item.leading}
      {item.icon ? (
        <img
          src={item.icon}
          alt=""
          className={classes.tagIcon}
          onLoad={onIconLoad}
        />
      ) : null}
      <span className={classes.tagText} title={item.text}>
        {item.text}
      </span>
    </>
  );

  if (item.to) {
    return (
      <Link
        to={item.to}
        state={item.state}
        className={className}
        style={style}
        data-tag-item={measure ? '' : undefined}
        onClick={item.onClick}
        onMouseEnter={item.onIntent}
        onPointerDown={item.onIntent}
      >
        {content}
      </Link>
    );
  }

  return (
    <span
      className={className}
      style={style}
      data-tag-item={measure ? '' : undefined}
    >
      {content}
    </span>
  );
};

function sumWidths(widths: number[], count: number, gap: number): number {
  if (count <= 0) return 0;
  let total = 0;
  for (let i = 0; i < count; i += 1) {
    total += widths[i];
    if (i > 0) total += gap;
  }
  return total;
}

function canPlaceFullTagsBeforeOverflow(
  containerWidth: number,
  tagWidths: number[],
  fullCount: number,
  gap: number,
  overflowWidth: number,
  minPartialWidth: number,
): boolean {
  const total = tagWidths.length;
  const remaining = total - fullCount;
  const used = sumWidths(tagWidths, fullCount, gap);
  const leadingGap = fullCount > 0 ? gap : 0;

  if (remaining <= 0) {
    return used <= containerWidth;
  }

  if (remaining === 1) {
    const space = containerWidth - used - leadingGap;
    return tagWidths[fullCount] <= space || space >= minPartialWidth;
  }

  const space = containerWidth - used - leadingGap - overflowWidth - gap;
  return space >= minPartialWidth;
}

function buildLayoutFromFullCount(
  containerWidth: number,
  tagWidths: number[],
  fullCount: number,
  gap: number,
  overflowWidth: number,
): TagRowLayout {
  const total = tagWidths.length;
  const remaining = total - fullCount;
  const used = sumWidths(tagWidths, fullCount, gap);
  const leadingGap = fullCount > 0 ? gap : 0;

  if (remaining <= 0) {
    return {
      fullCount: total,
      showPartial: false,
      showOverflow: false,
    };
  }

  if (remaining === 1) {
    const space = containerWidth - used - leadingGap;
    if (tagWidths[fullCount] <= space) {
      return {
        fullCount: total,
        showPartial: false,
        showOverflow: false,
      };
    }
    return {
      fullCount,
      showPartial: true,
      showOverflow: false,
      partialMaxWidth: space,
    };
  }

  const partialMaxWidth =
    containerWidth - used - leadingGap - overflowWidth - gap;

  return {
    fullCount,
    showPartial: true,
    showOverflow: true,
    partialMaxWidth,
  };
}

/**
 * 先尽量多放完整标签；若仍有剩余标签，预留「等N个」后把下一个标签截断展示。
 * 例：5 个标签只能完整放下 2 个 → [完整][完整][截断][等5个]
 */
function computeTagRowLayout(
  containerWidth: number,
  tagWidths: number[],
  gap: number,
  overflowWidth: number,
  minPartialWidth: number,
): TagRowLayout {
  const total = tagWidths.length;
  if (total === 0 || containerWidth <= 0) {
    return { fullCount: 0, showPartial: false, showOverflow: false };
  }

  if (sumWidths(tagWidths, total, gap) <= containerWidth) {
    return {
      fullCount: total,
      showPartial: false,
      showOverflow: false,
    };
  }

  let bestFullCount = 0;
  for (let fullCount = 0; fullCount < total; fullCount += 1) {
    if (
      canPlaceFullTagsBeforeOverflow(
        containerWidth,
        tagWidths,
        fullCount,
        gap,
        overflowWidth,
        minPartialWidth,
      )
    ) {
      bestFullCount = fullCount;
    }
  }

  return buildLayoutFromFullCount(
    containerWidth,
    tagWidths,
    bestFullCount,
    gap,
    overflowWidth,
  );
}

/** 单行标签：尽量完整展示；剩余标签用截断 + 「等N个」 */
const OverflowTagRow: FC<OverflowTagRowProps> = ({
  items,
  className,
  preset = 'feed',
}) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const measureRef = useRef<HTMLDivElement>(null);
  const [layout, setLayout] = useState<TagRowLayout>({
    fullCount: items.length,
    showPartial: false,
    showOverflow: false,
  });

  const overflowClass = PRESET_CLASS[preset].overflow;

  const measure = useCallback(() => {
    const container = containerRef.current;
    const measureRow = measureRef.current;
    if (!container || !measureRow) return;

    const maxWidth = container.clientWidth;
    const styles = getComputedStyle(measureRow);
    const gap = Number.parseFloat(styles.columnGap || styles.gap) || 6;
    const tagNodes =
      measureRow.querySelectorAll<HTMLElement>('[data-tag-item]');
    const overflowNode = measureRow.querySelector<HTMLElement>(
      '[data-overflow-measure]',
    );
    const partialNode = measureRow.querySelector<HTMLElement>(
      '[data-partial-measure]',
    );
    const tagWidths = Array.from(tagNodes).map(
      (node) => node.getBoundingClientRect().width,
    );
    const overflowWidth = overflowNode?.getBoundingClientRect().width ?? 0;
    const minPartialWidth = partialNode?.getBoundingClientRect().width ?? 36;

    if (maxWidth <= 0 || tagWidths.length === 0) {
      setLayout({
        fullCount: tagWidths.length,
        showPartial: false,
        showOverflow: false,
      });
      return;
    }

    setLayout(
      computeTagRowLayout(
        maxWidth,
        tagWidths,
        gap,
        overflowWidth,
        minPartialWidth,
      ),
    );
  }, []);

  useLayoutEffect(() => {
    setLayout({
      fullCount: items.length,
      showPartial: false,
      showOverflow: false,
    });
    const container = containerRef.current;
    if (!container) return undefined;

    measure();
    const observer = new ResizeObserver(() => measure());
    observer.observe(container);
    return () => observer.disconnect();
  }, [items, measure]);

  const hiddenItems = useMemo(() => {
    const start = layout.showPartial ? layout.fullCount + 1 : layout.fullCount;
    return items.slice(start);
  }, [items, layout.fullCount, layout.showPartial]);

  const hiddenTitle = useMemo(
    () => hiddenItems.map((item) => item.text).join('、'),
    [hiddenItems],
  );

  const partialItem = layout.showPartial ? items[layout.fullCount] : null;
  const partialStyle = useMemo(
    (): CSSProperties | undefined =>
      layout.partialMaxWidth
        ? { maxWidth: `${layout.partialMaxWidth}px` }
        : undefined,
    [layout.partialMaxWidth],
  );

  const overflowLabel = `等${items.length}个`;

  if (items.length === 0) return null;

  const rootClass = ['overflow-tag-row', className].filter(Boolean).join(' ');

  return (
    <div className={rootClass} ref={containerRef}>
      <div ref={measureRef} className="overflow-tag-row__measure" aria-hidden>
        {items.map((item) => (
          <TagChip
            key={item.key}
            item={item}
            preset={preset}
            measure
            onIconLoad={measure}
          />
        ))}
        <span
          data-partial-measure
          className="overflow-tag-row__partial-measure"
        >
          <TagChip
            item={{
              key: '__partial-measure__',
              text: '…',
              icon: items.find((item) => item.icon)?.icon,
            }}
            preset={preset}
            mode="partial"
          />
        </span>
        <span className={overflowClass} data-overflow-measure>
          {overflowLabel}
        </span>
      </div>

      <div className="overflow-tag-row__visible">
        {items.slice(0, layout.fullCount).map((item) => (
          <TagChip
            key={item.key}
            item={item}
            preset={preset}
            mode="full"
            onIconLoad={measure}
          />
        ))}
        {partialItem ? (
          <TagChip
            key={partialItem.key}
            item={partialItem}
            preset={preset}
            mode="partial"
            style={partialStyle}
            onIconLoad={measure}
          />
        ) : null}
        {layout.showOverflow ? (
          <span
            className={overflowClass}
            title={hiddenTitle}
            aria-label={`共 ${items.length} 个标签，未展示：${hiddenTitle}`}
          >
            {overflowLabel}
          </span>
        ) : null}
      </div>
    </div>
  );
};

export default memo(OverflowTagRow);
