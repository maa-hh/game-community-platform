import React, { memo } from 'react';
import type { FC } from 'react';

import DecoratedAvatar from '@/base-ui/DecoratedAvatar';
import UserAvatar from '@/base-ui/UserAvatar';
import {
  resolveAvatarFrameAsset,
  resolveAvatarFrameScale,
} from '@/constants/avatarFrameCatalog';
import { useDecorationRegistry } from '@/hooks/useDecorationRegistry';

export interface UserAvatarWithFrameProps {
  /** 对外 accountId；装扮接口按 accountId 查询并在服务端映射内部 userId。 */
  accountId?: number;
  name: string;
  src?: string;
  size?: number;
  className?: string;
  /** 显式传入时优先于 registry 自动解析 */
  frameUrl?: string;
  /** 为 false 时不自动拉取装扮（仅使用 frameUrl） */
  resolveFrame?: boolean;
}

const UserAvatarWithFrame: FC<UserAvatarWithFrameProps> = ({
  accountId,
  name,
  src,
  size = 36,
  className,
  frameUrl,
  resolveFrame = true,
}) => {
  const decoration = useDecorationRegistry(
    resolveFrame && frameUrl === undefined ? accountId : undefined,
  );
  const decorationAsset = resolveFrame
    ? resolveAvatarFrameAsset(
        decoration?.avatarFrame?.code,
        decoration?.avatarFrame?.assetJson,
      )
    : undefined;
  const resolvedFrameUrl = frameUrl ?? decorationAsset?.frameUrl;
  const resolvedFrameScale = resolveAvatarFrameScale(
    resolvedFrameUrl,
    decoration?.avatarFrame?.assetJson,
    decoration?.avatarFrame?.code,
  );
  const resolvedAvatarRatio = decorationAsset?.avatarRatio;

  if (resolvedFrameUrl) {
    return (
      <DecoratedAvatar
        name={name}
        src={src}
        size={size}
        frameUrl={resolvedFrameUrl}
        frameScale={resolvedFrameScale}
        avatarRatio={resolvedAvatarRatio}
        className={className}
      />
    );
  }

  return <UserAvatar name={name} src={src} size={size} className={className} />;
};

export default memo(UserAvatarWithFrame);
