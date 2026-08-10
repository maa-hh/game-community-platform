import React, { memo } from 'react';
import type { FC } from 'react';

import ShareCard from '@/components/ShareCard';

import type { IProps } from './types';

import './style.less';

/** 统一转发样式：附言 + 原帖 ShareCard */
const RepostBlock: FC<IProps> = ({
  quote,
  refPost,
  className,
  suppressCover = false,
}) => {
  return (
    <div className={`repost-block${className ? ` ${className}` : ''}`}>
      {quote ? <p className="repost-block__quote">{quote}</p> : null}
      <ShareCard data={refPost} suppressCover={suppressCover} />
    </div>
  );
};

export default memo(RepostBlock);
