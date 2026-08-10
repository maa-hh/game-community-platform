import React, { memo } from 'react';
import type { FC } from 'react';

import CoverPoster from '@/base-ui/CoverPoster';

export interface IGameCoverPosterProps {
  title: string;
  seed?: number;
  className?: string;
}

const GameCoverPoster: FC<IGameCoverPosterProps> = ({
  title,
  seed = 0,
  className,
}) => {
  return (
    <CoverPoster title={title} seed={seed} badge="GAME" className={className} />
  );
};

export default memo(GameCoverPoster);
