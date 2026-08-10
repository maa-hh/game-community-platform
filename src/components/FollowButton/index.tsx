import React, { memo } from 'react';
import type { FC } from 'react';
import { Button } from 'antd';

import type { FollowButtonProps } from './types';

/** 统一关注按钮：差异仅 followed */
const FollowButton: FC<FollowButtonProps> = ({
  followed,
  onClick,
  size = 'small',
  className,
}) => {
  return (
    <Button
      size={size}
      type={followed ? 'default' : 'primary'}
      className={className}
      onClick={onClick}
    >
      {followed ? '已关注' : '关注'}
    </Button>
  );
};

export default memo(FollowButton);

export type { FollowButtonProps } from './types';
