import React, { memo } from 'react';
import type { FC } from 'react';
import { Empty, Button } from 'antd';

import type { EmptyStateProps } from './types';

import './style.less';

/** 统一空状态 */
const EmptyState: FC<EmptyStateProps> = ({
  description = '暂无内容',
  actionText,
  onAction,
  className,
}) => {
  return (
    <div className={`empty-state${className ? ` ${className}` : ''}`}>
      <Empty description={description}>
        {actionText && onAction ? (
          <Button type="primary" onClick={onAction}>
            {actionText}
          </Button>
        ) : null}
      </Empty>
    </div>
  );
};

export default memo(EmptyState);

export type { EmptyStateProps } from './types';
