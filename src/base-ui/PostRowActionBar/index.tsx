import React, { memo } from 'react';
import type { FC } from 'react';

import type { PostRowActionBarProps } from './types';

import './style.less';

const PostRowActionBar: FC<PostRowActionBarProps> = ({ items, className }) => {
  if (items.length === 0) return null;

  const rootClass = ['post-row-action-bar', className]
    .filter(Boolean)
    .join(' ');

  return (
    <div
      className={rootClass}
      onClick={(event) => event.stopPropagation()}
      onKeyDown={(event) => event.stopPropagation()}
      role="presentation"
    >
      {items.map((item) => (
        <button
          key={item.key}
          type="button"
          className={`post-row-action-bar__btn${
            item.danger ? ' is-danger' : ''
          }`}
          onClick={item.onClick}
        >
          {item.label}
        </button>
      ))}
    </div>
  );
};

export default memo(PostRowActionBar);

export type { PostRowActionItem, PostRowActionBarProps } from './types';
