import React, { memo } from 'react';
import type { FC, ReactNode } from 'react';

import MasonryGrid from '@/base-ui/MasonryGrid';

export interface IGameMasonryGridProps {
  children: ReactNode;
  className?: string;
}

/** 自适应列瀑布流容器 */
const GameMasonryGrid: FC<IGameMasonryGridProps> = ({
  children,
  className,
}) => {
  return (
    <MasonryGrid
      fillOrder="row"
      className={`games-page__waterfall${className ? ` ${className}` : ''}`}
    >
      {children}
    </MasonryGrid>
  );
};

export default memo(GameMasonryGrid);
