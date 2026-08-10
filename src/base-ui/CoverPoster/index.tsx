import React, { memo } from 'react';
import type { FC } from 'react';

import './style.less';

const POSTER_THEMES = [
  {
    bg: 'linear-gradient(145deg, #fff4e8 0%, #ffe0c2 100%)',
    accent: '#ff6600',
  },
  {
    bg: 'linear-gradient(145deg, #eef6ff 0%, #d9e8ff 100%)',
    accent: '#3b82f6',
  },
  {
    bg: 'linear-gradient(145deg, #f2fbf3 0%, #d7f0dc 100%)',
    accent: '#22a06b',
  },
  {
    bg: 'linear-gradient(145deg, #f7f2ff 0%, #e8dcff 100%)',
    accent: '#8b5cf6',
  },
  {
    bg: 'linear-gradient(145deg, #fff1f2 0%, #ffd9de 100%)',
    accent: '#e85d75',
  },
] as const;

export interface ICoverPosterProps {
  title: string;
  seed?: number;
  badge?: string;
  className?: string;
}

const CoverPoster: FC<ICoverPosterProps> = ({
  title,
  seed = 0,
  badge = 'POST',
  className,
}) => {
  const theme = POSTER_THEMES[Math.abs(seed) % POSTER_THEMES.length];

  return (
    <div
      className={`cover-poster${className ? ` ${className}` : ''}`}
      style={{ background: theme.bg }}
      aria-hidden
    >
      <div
        className="cover-poster__orb cover-poster__orb--left"
        style={{ backgroundColor: theme.accent }}
      />
      <div
        className="cover-poster__orb cover-poster__orb--right"
        style={{ backgroundColor: theme.accent }}
      />
      <span
        className="cover-poster__badge"
        style={{ color: theme.accent, borderColor: `${theme.accent}55` }}
      >
        {badge}
      </span>
      <p className="cover-poster__title">{title}</p>
    </div>
  );
};

export default memo(CoverPoster);
