import React, {
  memo,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import type { FC, MouseEvent } from 'react';
import { LeftOutlined, RightOutlined } from '@ant-design/icons';

import ImageLightbox from '@/base-ui/ImageLightbox';
import LazyImage from '@/base-ui/LazyImage';

import './style.less';

export interface CoverGalleryImage {
  src: string;
  previewSrc?: string;
}

interface IProps {
  images: Array<string | CoverGalleryImage>;
  className?: string;
}

function normalizeImages(images: Array<string | CoverGalleryImage>) {
  return images
    .map((item) => {
      if (typeof item === 'string') {
        return { src: item, previewSrc: item };
      }
      return {
        src: item.src,
        previewSrc: item.previewSrc || item.src,
      };
    })
    .filter((item) => Boolean(item.src));
}

/** 图文封面：横向滑动多图；单图按自然比例展示 */
const CoverGallery: FC<IProps> = ({ images, className }) => {
  const viewportRef = useRef<HTMLDivElement>(null);
  const normalized = useMemo(() => normalizeImages(images), [images]);
  const [preview, setPreview] = useState<{ open: boolean; current: number }>({
    open: false,
    current: 0,
  });
  const [canPrev, setCanPrev] = useState(false);
  const [canNext, setCanNext] = useState(false);
  const total = normalized.length;
  const previewImages = normalized.map((item) => item.previewSrc);
  const isSingle = total === 1;

  const updateScrollState = useCallback(() => {
    const el = viewportRef.current;
    if (!el) return;

    const max = el.scrollWidth - el.clientWidth;
    setCanPrev(el.scrollLeft > 2);
    setCanNext(max > 2 && el.scrollLeft < max - 2);
  }, []);

  useEffect(() => {
    const el = viewportRef.current;
    if (!el) return undefined;
    el.scrollLeft = 0;
    updateScrollState();
    el.addEventListener('scroll', updateScrollState, { passive: true });
    const ro = new ResizeObserver(updateScrollState);
    ro.observe(el);
    return () => {
      el.removeEventListener('scroll', updateScrollState);
      ro.disconnect();
    };
  }, [normalized, updateScrollState]);

  if (total === 0) return null;

  const reveal = (dir: -1 | 1, e?: MouseEvent) => {
    e?.stopPropagation();
    e?.preventDefault();
    const el = viewportRef.current;
    if (!el) return;
    const step = Math.max(el.clientWidth * 0.72, 160);
    el.scrollBy({ left: dir * step, behavior: 'smooth' });
  };

  const openAt = (i: number) => {
    setPreview({ open: true, current: i });
  };

  const rootClass = [
    'cover-gallery',
    isSingle ? 'cover-gallery--single' : 'cover-gallery--multi',
    className,
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <div className={rootClass}>
      <div ref={viewportRef} className="cover-gallery__viewport">
        <div className="cover-gallery__track">
          {normalized.map((item, i) => (
            <button
              key={`${item.src}-${i}`}
              type="button"
              className="cover-gallery__slide"
              onClick={() => openAt(i)}
            >
              <LazyImage
                src={item.src}
                alt=""
                className="cover-gallery__image"
                imgClassName="cover-gallery__image-el"
                fallback={<span className="cover-gallery__image-fallback" />}
                onLoad={updateScrollState}
              />
              <span className="cover-gallery__counter" aria-hidden>
                {i + 1}/{total}
              </span>
            </button>
          ))}
        </div>
      </div>

      {!isSingle && (
        <>
          <button
            type="button"
            className="cover-gallery__nav is-prev"
            aria-label="向左露出更多"
            disabled={!canPrev}
            onClick={(e) => reveal(-1, e)}
          >
            <LeftOutlined />
          </button>
          <button
            type="button"
            className="cover-gallery__nav is-next"
            aria-label="向右露出更多"
            disabled={!canNext}
            onClick={(e) => reveal(1, e)}
          >
            <RightOutlined />
          </button>
        </>
      )}

      <ImageLightbox
        open={preview.open}
        images={previewImages}
        current={preview.current}
        onChange={(current) => setPreview((p) => ({ ...p, current }))}
        onClose={() => setPreview((p) => ({ ...p, open: false }))}
      />
    </div>
  );
};

export default memo(CoverGallery);
