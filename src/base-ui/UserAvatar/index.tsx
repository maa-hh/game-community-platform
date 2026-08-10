import React, { memo } from 'react';
import type { FC } from 'react';
import { Avatar } from 'antd';

import './style.less';

export interface UserAvatarProps {
  name: string;
  src?: string;
  size?: number;
  className?: string;
  /** 渐变色块 fallback（非 antd Avatar 时） */
  variant?: 'antd' | 'block';
}

/** 统一用户头像：有图用图，无图用昵称首字 */
const UserAvatar: FC<UserAvatarProps> = ({
  name,
  src,
  size = 36,
  className,
  variant = 'antd',
}) => {
  const initial = name?.slice(0, 1)?.toUpperCase() || '?';
  const cls = `user-avatar${className ? ` ${className}` : ''}`;
  const imageSrc = src?.trim() || undefined;

  if (variant === 'block') {
    if (imageSrc) {
      return (
        <img
          className={`${cls} user-avatar--block`}
          src={imageSrc}
          alt=""
          style={{ width: size, height: size }}
        />
      );
    }
    return (
      <div
        className={`${cls} user-avatar--block is-fallback`}
        style={{
          width: size,
          height: size,
          fontSize: Math.max(12, size * 0.4),
        }}
      >
        {initial}
      </div>
    );
  }

  return (
    <Avatar size={size} src={imageSrc} className={cls}>
      {initial}
    </Avatar>
  );
};

export default memo(UserAvatar);
