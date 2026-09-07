import React, { memo, useEffect } from 'react';
import type { FC } from 'react';
import { createPortal } from 'react-dom';
import { CloseOutlined, LeftOutlined, RightOutlined } from '@ant-design/icons';

import './style.less';

interface IProps {
  open: boolean;
  images: string[];
  current: number;
  onChange: (index: number) => void;
  onClose: () => void;
}

/**
 * 大图预览：与封面同构的居中图框 + 左右半透明切换（替代原进度条位置），
 * 每次切一张；点遮罩 / Esc / 关闭按钮退出。
 */
const ImageLightbox: FC<IProps> = ({
  open,
  images,
  current,
  onChange,
  onClose,
}) => {
  const total = images.length;
  const index = Math.min(Math.max(current, 0), Math.max(total - 1, 0));

  useEffect(() => {
    if (!open) return undefined;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
      if (total <= 1) return;
      if (e.key === 'ArrowLeft') onChange((index - 1 + total) % total);
      if (e.key === 'ArrowRight') onChange((index + 1) % total);
    };
    window.addEventListener('keydown', onKey);
    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      window.removeEventListener('keydown', onKey);
      document.body.style.overflow = prevOverflow;
    };
  }, [open, index, total, onChange, onClose]);

  if (!open || total === 0) return null;

  return createPortal(
    <div className="image-lightbox" role="dialog" aria-modal="true">
      <button
        type="button"
        className="image-lightbox__mask"
        aria-label="关闭预览"
        onClick={onClose}
      />

      <div className="image-lightbox__frame">
        <button
          type="button"
          className="image-lightbox__close"
          aria-label="关闭"
          onClick={onClose}
        >
          <CloseOutlined />
        </button>

        <div className="image-lightbox__stage">
          <img
            key={images[index]}
            src={images[index]}
            alt=""
            className="image-lightbox__image image-lightbox__image-el"
            loading="eager"
            draggable={false}
          />
        </div>

        {total > 1 && (
          <>
            <button
              type="button"
              className="image-lightbox__nav is-prev"
              aria-label="上一张"
              onClick={() => onChange((index - 1 + total) % total)}
            >
              <LeftOutlined />
            </button>
            <button
              type="button"
              className="image-lightbox__nav is-next"
              aria-label="下一张"
              onClick={() => onChange((index + 1) % total)}
            >
              <RightOutlined />
            </button>
            <span className="image-lightbox__indicator">
              {index + 1}/{total}
            </span>
          </>
        )}
      </div>
    </div>,
    document.body,
  );
};

export default memo(ImageLightbox);
