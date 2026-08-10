import React, { memo } from 'react';
import type { FC, ReactNode } from 'react';
import { Button } from 'antd';
import { ArrowLeftOutlined } from '@ant-design/icons';

import './style.less';

export interface PageSubTopBarProps {
  /** 纯文字标题（与 center 二选一） */
  title?: string;
  /** 自定义中间区域，如头像 + 昵称 */
  center?: ReactNode;
  onBack: () => void;
  extra?: ReactNode;
  className?: string;
}

/** 站内二级页顶栏：返回 + 标题 + 右侧扩展区（与帖子详情/创作中心同款） */
const PageSubTopBar: FC<PageSubTopBarProps> = ({
  title,
  center,
  onBack,
  extra,
  className,
}) => {
  const rootClass = ['page-sub-top-bar', className].filter(Boolean).join(' ');

  return (
    <header className={rootClass}>
      <Button
        type="text"
        className="page-sub-top-bar__back"
        icon={<ArrowLeftOutlined />}
        aria-label="返回"
        onClick={onBack}
      />

      <div className="page-sub-top-bar__meta">
        {center ?? <h1 className="page-sub-top-bar__title">{title}</h1>}
      </div>

      {extra ? <div className="page-sub-top-bar__extra">{extra}</div> : null}
    </header>
  );
};

export default memo(PageSubTopBar);
