import React, { memo, useCallback } from 'react';
import type { FC, KeyboardEvent, MouseEvent, ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';

import UserAvatarWithFrame from '@/base-ui/UserAvatarWithFrame';

import './style.less';

export interface ProfileUserLinkProps {
  accountId?: number;
  nickname: string;
  avatar?: string;
  avatarFrameUrl?: string;
  size?: number;
  /** 在顶栏等紧凑区域不让头像框扩大布局占位。 */
  compactFrame?: boolean;
  showAvatar?: boolean;
  showNickname?: boolean;
  /** 昵称下方的辅助信息（如时间），不触发昵称 hover */
  subline?: ReactNode;
  className?: string;
  stopPropagation?: boolean;
}

function useProfileNavigation(accountId?: number, stopPropagation = true) {
  const navigate = useNavigate();
  const canNavigate = Boolean(accountId);

  const goProfile = useCallback(
    (event: MouseEvent<HTMLElement> | KeyboardEvent<HTMLElement>) => {
      if (stopPropagation) event.stopPropagation();
      if (!accountId) return;
      navigate(`/profile?accountId=${accountId}`);
    },
    [accountId, navigate, stopPropagation],
  );

  const onKeyDown = useCallback(
    (event: KeyboardEvent<HTMLElement>) => {
      if (event.key !== 'Enter') return;
      event.preventDefault();
      goProfile(event);
    },
    [goProfile],
  );

  return { canNavigate, goProfile, onKeyDown };
}

interface ProfileUserLinkPartProps {
  canNavigate: boolean;
  className?: string;
  onClick: (event: MouseEvent<HTMLElement>) => void;
  onKeyDown: (event: KeyboardEvent<HTMLElement>) => void;
  children: ReactNode;
}

const ProfileUserLinkPart: FC<ProfileUserLinkPartProps> = ({
  canNavigate,
  className,
  onClick,
  onKeyDown,
  children,
}) => (
  <span
    className={`profile-user-link${canNavigate ? ' is-clickable' : ''}${
      className ? ` ${className}` : ''
    }`}
    role={canNavigate ? 'button' : undefined}
    tabIndex={canNavigate ? 0 : undefined}
    onClick={canNavigate ? onClick : undefined}
    onKeyDown={canNavigate ? onKeyDown : undefined}
  >
    {children}
  </span>
);

const ProfileUserLink: FC<ProfileUserLinkProps> = ({
  accountId,
  nickname,
  avatar,
  avatarFrameUrl,
  size = 32,
  compactFrame = false,
  showAvatar = true,
  showNickname = true,
  subline,
  className,
  stopPropagation = true,
}) => {
  const { canNavigate, goProfile, onKeyDown } = useProfileNavigation(
    accountId,
    stopPropagation,
  );

  const resolvedFrameUrl = avatarFrameUrl;

  const showBoth = showAvatar && showNickname;

  const avatarNode = (
    <UserAvatarWithFrame
      accountId={accountId}
      name={nickname}
      src={avatar}
      size={size}
      compactFrame={compactFrame}
      frameUrl={resolvedFrameUrl}
      resolveFrame={resolvedFrameUrl === undefined}
      className="profile-user-link__avatar"
    />
  );

  if (showBoth) {
    return (
      <span
        className={`profile-user-link-group${className ? ` ${className}` : ''}`}
      >
        <ProfileUserLinkPart
          canNavigate={canNavigate}
          className="profile-user-link--avatar"
          onClick={goProfile}
          onKeyDown={onKeyDown}
        >
          <span className="profile-user-link__avatar-wrap">{avatarNode}</span>
        </ProfileUserLinkPart>
        <ProfileUserLinkPart
          canNavigate={canNavigate}
          className="profile-user-link--name"
          onClick={goProfile}
          onKeyDown={onKeyDown}
        >
          <span className="profile-user-link__meta">
            <span className="profile-user-link__name">{nickname}</span>
            {subline ? (
              <span className="profile-user-link__subline">{subline}</span>
            ) : null}
          </span>
        </ProfileUserLinkPart>
      </span>
    );
  }

  if (showAvatar) {
    return (
      <ProfileUserLinkPart
        canNavigate={canNavigate}
        className={`profile-user-link--avatar${className ? ` ${className}` : ''}`}
        onClick={goProfile}
        onKeyDown={onKeyDown}
      >
        <span className="profile-user-link__avatar-wrap">{avatarNode}</span>
      </ProfileUserLinkPart>
    );
  }

  if (showNickname) {
    return (
      <ProfileUserLinkPart
        canNavigate={canNavigate}
        className={`profile-user-link--name${className ? ` ${className}` : ''}`}
        onClick={goProfile}
        onKeyDown={onKeyDown}
      >
        <span className="profile-user-link__meta">
          <span className="profile-user-link__name">{nickname}</span>
          {subline ? (
            <span className="profile-user-link__subline">{subline}</span>
          ) : null}
        </span>
      </ProfileUserLinkPart>
    );
  }

  return null;
};

export default memo(ProfileUserLink);
