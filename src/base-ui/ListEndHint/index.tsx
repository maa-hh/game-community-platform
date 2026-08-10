import React, { forwardRef, memo } from 'react';
import { Spin, Typography } from 'antd';

import './style.less';

const { Text } = Typography;

export interface IListEndHintProps {
  loadingMore?: boolean;
  hasMore?: boolean;
  /** 已有内容条数；为 0 时不渲染，避免与空态冲突 */
  itemCount: number;
  endText?: string;
  className?: string;
}

const ListEndHint = forwardRef<HTMLDivElement, IListEndHintProps>(
  (
    {
      loadingMore = false,
      hasMore = true,
      itemCount,
      endText = '已经翻到底了哦',
      className,
    },
    ref,
  ) => {
    if (itemCount <= 0) return null;

    return (
      <div
        ref={ref}
        className={`list-end-hint${className ? ` ${className}` : ''}`}
        aria-hidden={hasMore && !loadingMore}
      >
        {loadingMore ? <Spin size="small" /> : null}
        {!hasMore && !loadingMore ? (
          <Text type="secondary">{endText}</Text>
        ) : null}
      </div>
    );
  },
);

ListEndHint.displayName = 'ListEndHint';

export default memo(ListEndHint);
