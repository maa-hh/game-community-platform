import React, {
  Children,
  memo,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import type { FC, ReactNode } from 'react';

import './style.less';

export type MasonryFillOrder = 'column' | 'row';

export interface IMasonryGridProps {
  children: ReactNode;
  className?: string;
  /**
   * column：CSS 多列，浏览器按列平衡
   * row：严格按索引轮询分列（1→2→3，4→5→6…），保证阅读顺序
   */
  fillOrder?: MasonryFillOrder;
}

const MASONRY_COL_MIN = 280;
const MASONRY_COL_MIN_MOBILE = 260;
const MASONRY_GAP = 2;

function useMasonryColumnCount(
  containerRef: React.RefObject<HTMLDivElement | null>,
  enabled: boolean,
) {
  const [columnCount, setColumnCount] = useState(1);

  useLayoutEffect(() => {
    if (!enabled) return undefined;

    const el = containerRef.current;
    if (!el) return undefined;

    const update = () => {
      const width = el.clientWidth;
      // KeepAlive 使用 hidden 暂时收起父页时宽度会变为 0。不能因此把
      // 已计算好的多列布局重置为单列，否则返回时必然重排并改变滚动锚点。
      if (width <= 0) return;
      const colMin = width <= 480 ? MASONRY_COL_MIN_MOBILE : MASONRY_COL_MIN;
      setColumnCount(
        Math.max(1, Math.floor((width + MASONRY_GAP) / (colMin + MASONRY_GAP))),
      );
    };

    update();
    const observer = new ResizeObserver(update);
    observer.observe(el);
    return () => observer.disconnect();
  }, [containerRef, enabled]);

  return columnCount;
}

/** 自适应列瀑布流容器 */
const MasonryGrid: FC<IMasonryGridProps> = ({
  children,
  className,
  fillOrder = 'column',
}) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const isRowFill = fillOrder === 'row';
  const columnCount = useMasonryColumnCount(containerRef, isRowFill);
  const items = Children.toArray(children);

  const columns = useMemo(() => {
    if (!isRowFill) return null;

    const cols: ReactNode[][] = Array.from({ length: columnCount }, () => []);
    items.forEach((child, index) => {
      cols[index % columnCount].push(child);
    });
    return cols;
  }, [columnCount, isRowFill, items]);

  if (isRowFill) {
    return (
      <div
        ref={containerRef}
        className={`masonry-grid masonry-grid--row-fill${
          className ? ` ${className}` : ''
        }`}
      >
        {columns?.map((col, index) => (
          <div key={index} className="masonry-grid__col">
            {col}
          </div>
        ))}
      </div>
    );
  }

  return (
    <div className={`masonry-grid${className ? ` ${className}` : ''}`}>
      {children}
    </div>
  );
};

export default memo(MasonryGrid);
