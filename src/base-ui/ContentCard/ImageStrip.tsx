import React, {
  memo,
  useCallback,
  useLayoutEffect,
  useRef,
  useState,
} from 'react';
import type { FC } from 'react';

interface IProps {
  images: string[];
}

/**
 * 单行限高 + 等比图片条。
 * 只展示能完整放下的图；放不下时末张完整图右上角标「共 N 张」。
 */
const ImageStrip: FC<IProps> = ({ images }) => {
  const measureRef = useRef<HTMLDivElement>(null);
  const [visibleCount, setVisibleCount] = useState(images.length);

  const measure = useCallback(() => {
    const row = measureRef.current;
    if (!row || images.length === 0) {
      setVisibleCount(0);
      return;
    }

    const maxWidth = row.clientWidth;
    const styles = getComputedStyle(row);
    const gap = Number.parseFloat(styles.columnGap || styles.gap) || 8;
    const items = Array.from(
      row.querySelectorAll<HTMLElement>('[data-measure-item]'),
    );

    let used = 0;
    let count = 0;
    for (let i = 0; i < items.length; i += 1) {
      const width = items[i].offsetWidth;
      if (width <= 0) {
        // 图片未加载完，先尽量用已有宽度；其余等 onLoad 再量
        break;
      }
      const next = count === 0 ? width : used + gap + width;
      if (next > maxWidth + 0.5) break;
      used = next;
      count += 1;
    }

    // 至少露出 1 张，避免空白
    const nextCount =
      count > 0 ? count : images.length > 0 && maxWidth > 0 ? 1 : 0;
    setVisibleCount(Math.min(nextCount, images.length));
  }, [images.length]);

  useLayoutEffect(() => {
    setVisibleCount(images.length);
    const row = measureRef.current;
    if (!row) return undefined;

    measure();
    const ro = new ResizeObserver(() => measure());
    ro.observe(row);
    return () => ro.disconnect();
  }, [measure, images]);

  if (images.length === 0) return null;

  const displayImages = images.slice(0, Math.max(visibleCount, 1));
  const showBadge = images.length > displayImages.length;

  return (
    <div className="content-card__images-wrap">
      {/* 隐藏测量层：量「完整放下」张数，避免角标贴在被裁切的半张图上 */}
      <div
        ref={measureRef}
        className="content-card__images content-card__images--measure"
        aria-hidden
      >
        {images.map((src, index) => (
          <div
            key={`m-${src}-${index}`}
            className="content-card__media"
            data-measure-item
          >
            <img src={src} alt="" draggable={false} onLoad={measure} />
          </div>
        ))}
      </div>

      <div className="content-card__images">
        {displayImages.map((src, index) => (
          <div key={`${src}-${index}`} className="content-card__media">
            <img src={src} alt="" loading="lazy" draggable={false} />
            {showBadge && index === displayImages.length - 1 && (
              <span
                className="content-card__count"
                aria-label={`共${images.length}张`}
              >
                共{images.length}张
              </span>
            )}
          </div>
        ))}
      </div>
    </div>
  );
};

export default memo(ImageStrip);
