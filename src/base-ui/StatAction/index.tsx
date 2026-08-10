import React, { memo } from 'react';
import type { FC, MouseEventHandler, ReactNode } from 'react';
import {
  EyeOutlined,
  LikeFilled,
  LikeOutlined,
  MessageOutlined,
  ShareAltOutlined,
  StarFilled,
  StarOutlined,
} from '@ant-design/icons';

import { formatCount } from '@/utils/formatCount';

import './style.less';

export type StatActionKind =
  'like' | 'favorite' | 'share' | 'comment' | 'view' | 'reply';

export interface StatActionProps {
  kind: StatActionKind;
  active?: boolean;
  count?: number;
  /** 覆盖默认文案（如「回复」） */
  label?: ReactNode;
  size?: 'sm' | 'md';
  disabled?: boolean;
  className?: string;
  stopPropagation?: boolean;
  onClick?: MouseEventHandler<HTMLButtonElement>;
}

function defaultIcon(kind: StatActionKind, active?: boolean): ReactNode {
  switch (kind) {
    case 'like':
      return active ? <LikeFilled /> : <LikeOutlined />;
    case 'favorite':
      return active ? <StarFilled /> : <StarOutlined />;
    case 'share':
      return <ShareAltOutlined />;
    case 'comment':
    case 'reply':
      return <MessageOutlined />;
    case 'view':
      return <EyeOutlined />;
    default:
      return null;
  }
}

function defaultLabel(kind: StatActionKind): string | null {
  if (kind === 'reply') return '回复';
  return null;
}

/**
 * 统一互动按钮：点赞 / 收藏 / 分享 / 评论 / 浏览 / 回复
 * 差异只由 kind + active + count / label 决定
 */
const StatAction: FC<StatActionProps> = ({
  kind,
  active,
  count,
  label,
  size = 'md',
  disabled,
  className,
  stopPropagation,
  onClick,
}) => {
  const isButton = Boolean(onClick) && kind !== 'view';
  const text =
    label !== undefined
      ? label
      : count != null
        ? formatCount(count)
        : defaultLabel(kind);

  const cls = [
    'stat-action',
    `stat-action--${size}`,
    active ? 'is-active' : '',
    className || '',
  ]
    .filter(Boolean)
    .join(' ');

  const handleClick: MouseEventHandler<HTMLButtonElement> = (e) => {
    if (stopPropagation) e.stopPropagation();
    onClick?.(e);
  };

  if (!isButton) {
    return (
      <span className={cls} title={kind === 'view' ? '浏览' : undefined}>
        {defaultIcon(kind, active)}
        {text != null && text !== '' ? <span>{text}</span> : null}
      </span>
    );
  }

  return (
    <button
      type="button"
      className={cls}
      disabled={disabled}
      onClick={handleClick}
    >
      {defaultIcon(kind, active)}
      {text != null && text !== '' ? <span>{text}</span> : null}
    </button>
  );
};

export default memo(StatAction);
