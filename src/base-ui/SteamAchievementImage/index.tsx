import React, { memo, useCallback, useEffect, useMemo, useState } from 'react';
import type { FC, ReactNode } from 'react';

import { steamAchievementImageCandidates } from '@/utils/steamImage';

export interface SteamAchievementImageProps {
  src?: string | null;
  alt?: string;
  className?: string;
  fallback?: ReactNode;
}

/** Steam 成就图标：旧 CDN 失败时自动切换到当前可用 CDN。 */
const SteamAchievementImage: FC<SteamAchievementImageProps> = ({
  src: iconUrl,
  alt = '',
  className,
  fallback = null,
}) => {
  const candidates = useMemo(
    () => steamAchievementImageCandidates(iconUrl),
    [iconUrl],
  );
  const [index, setIndex] = useState(0);

  useEffect(() => {
    setIndex(0);
  }, [candidates]);

  const handleError = useCallback(() => {
    setIndex((current) => current + 1);
  }, []);

  const imageUrl = index < candidates.length ? candidates[index] : undefined;
  if (!imageUrl) {
    return <>{fallback}</>;
  }

  return (
    <img
      src={imageUrl}
      alt={alt}
      className={className}
      loading="lazy"
      onError={handleError}
    />
  );
};

export default memo(SteamAchievementImage);
