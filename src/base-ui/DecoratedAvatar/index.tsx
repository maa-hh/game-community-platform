import React, { memo } from 'react';
import type { FC } from 'react';

import UserAvatar from '@/base-ui/UserAvatar';

import './style.less';

const DEFAULT_FRAME_SCALE = 1.48;

export interface DecoratedAvatarProps {
  name: string;
  src?: string;
  size?: number;
  frameUrl?: string;
  frameScale?: number;
  /** 头像在挂件容器中的占比，默认约 0.64（B 站风格留边给装饰） */
  avatarRatio?: number;
  className?: string;
  wrapperClassName?: string;
}

const DecoratedAvatar: FC<DecoratedAvatarProps> = ({
  name,
  src,
  size = 36,
  frameUrl,
  frameScale = DEFAULT_FRAME_SCALE,
  avatarRatio = 0.64,
  className,
  wrapperClassName,
}) => {
  const scale = frameUrl ? frameScale : 1;
  const containerSize = Math.round(size * scale);
  const avatarSize = frameUrl ? Math.round(containerSize * avatarRatio) : size;

  return (
    <span
      className={`decorated-avatar${frameUrl ? ' is-framed' : ''}${
        wrapperClassName ? ` ${wrapperClassName}` : ''
      }`}
      style={{ width: containerSize, height: containerSize }}
    >
      <UserAvatar
        name={name}
        src={src}
        size={avatarSize}
        className={['decorated-avatar__avatar', className]
          .filter(Boolean)
          .join(' ')}
      />
      {frameUrl ? (
        <img className="decorated-avatar__frame" src={frameUrl} alt="" />
      ) : null}
    </span>
  );
};

export default memo(DecoratedAvatar);
