import React, { memo } from 'react';
import type { FC } from 'react';
import { Spin } from 'antd';

import './style.less';

interface IProps {
  tip?: string;
  className?: string;
  /** 占满内容区最小高度 */
  full?: boolean;
}

/** 统一页面/区块加载态 */
const PageLoading: FC<IProps> = ({ tip, className, full }) => {
  return (
    <div
      className={`page-loading${full ? ' is-full' : ''}${
        className ? ` ${className}` : ''
      }`}
    >
      <Spin tip={tip} />
    </div>
  );
};

export default memo(PageLoading);
