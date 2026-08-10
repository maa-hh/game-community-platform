import React, { memo } from 'react';
import type { FC } from 'react';

import type { IProps } from './types';

import './style.less';

/** 作者管理操作：头像行右侧文字链 */
const PostOwnerLinks: FC<IProps> = ({ items, className }) => {
  if (items.length === 0) return null;

  return (
    <div
      className={`post-owner-links${className ? ` ${className}` : ''}`}
      onClick={(e) => e.stopPropagation()}
      onKeyDown={(e) => e.stopPropagation()}
      role="presentation"
    >
      {items.map((item, index) => (
        <React.Fragment key={item.key}>
          {index > 0 && <span className="post-owner-links__sep">·</span>}
          <button
            type="button"
            className={`post-owner-links__btn${
              item.danger ? ' is-danger' : ''
            }`}
            onClick={item.onClick}
          >
            {item.label}
          </button>
        </React.Fragment>
      ))}
    </div>
  );
};

export default memo(PostOwnerLinks);

export type { PostOwnerLinkItem } from './types';
