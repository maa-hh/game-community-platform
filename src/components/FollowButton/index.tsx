import React, { memo } from 'react';
import type { FC } from 'react';
import { Button } from 'antd';

import type { FollowButtonProps } from './types';

/** 统一关注按钮：差异仅 followed */
const FollowButton: FC<FollowButtonProps> = ({
  followed,
  onClick,
  disabled,
  size = 'small',
  className,
  followText = '关注',
  followedText = '已关注',
}) => {
  return (
    <Button
      size={size}
      type={followed ? 'default' : 'primary'}
      className={className}
      onClick={onClick}
      disabled={disabled}
    >
      {followed ? followedText : followText}
    </Button>
  );
};

export default memo(FollowButton);

export type { FollowButtonProps } from './types';
