import React, { memo } from 'react';
import type { FC } from 'react';
import { Button } from 'antd';
import { ShareAltOutlined } from '@ant-design/icons';

import ProfileUserLink from '@/components/ProfileUserLink';
import FollowButton from '@/components/FollowButton';

import type { IProps } from './types';

import './style.less';

/** 作者区滚出后吸顶横幅（与 AuthorHeader 同构，差异仅布局位置） */
const AuthorScrollBanner: FC<IProps> = ({
  visible,
  author,
  followed,
  isOwner,
  onFollow,
  onShare,
}) => {
  return (
    <div
      className={`author-scroll-banner${visible ? ' is-visible' : ''}`}
      aria-hidden={!visible}
    >
      <div className="author-scroll-banner__main">
        <ProfileUserLink
          accountId={author.accountId}
          nickname={author.nickname}
          avatar={author.avatar}
          size={32}
          className="author-scroll-banner__author"
        />
      </div>
      <div className="author-scroll-banner__actions">
        {!isOwner && <FollowButton followed={followed} onClick={onFollow} />}
        <Button
          size="small"
          type="text"
          icon={<ShareAltOutlined />}
          onClick={onShare}
          aria-label="分享"
        />
      </div>
    </div>
  );
};

export default memo(AuthorScrollBanner);
