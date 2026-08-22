import React, { memo } from 'react';
import type { FC } from 'react';
import { Button, Dropdown } from 'antd';
import type { MenuProps } from 'antd';
import {
  ArrowLeftOutlined,
  MoreOutlined,
  ShareAltOutlined,
} from '@ant-design/icons';

import ProfileUserLink from '@/components/ProfileUserLink';
import FollowButton from '@/components/FollowButton';
import type { PostAuthor } from '@/types/post';

import './PostDetailTopBar.less';

export interface PostDetailTopBarProps {
  author: PostAuthor;
  avatarFrameUrl?: string;
  createdAt?: string;
  followed: boolean;
  isOwner: boolean;
  moreMenu?: MenuProps['items'];
  onBack: () => void;
  onFollow: () => void;
  onShare: () => void;
}

const PostDetailTopBar: FC<PostDetailTopBarProps> = ({
  author,
  avatarFrameUrl,
  createdAt,
  followed,
  isOwner,
  moreMenu,
  onBack,
  onFollow,
  onShare,
}) => {
  const hasMore = Boolean(moreMenu && moreMenu.length > 0);

  return (
    <header className="post-detail-top-bar">
      <Button
        type="text"
        className="post-detail-top-bar__back"
        icon={<ArrowLeftOutlined />}
        aria-label="返回"
        onMouseDown={(event) => event.preventDefault()}
        onClick={onBack}
      />

      <div className="post-detail-top-bar__author">
        <ProfileUserLink
          accountId={author.accountId}
          nickname={author.nickname}
          avatar={author.avatar}
          avatarFrameUrl={avatarFrameUrl}
          size={32}
          showNickname={false}
          className="post-detail-top-bar__avatar"
        />
        <div className="post-detail-top-bar__meta">
          <ProfileUserLink
            accountId={author.accountId}
            nickname={author.nickname}
            avatar={author.avatar}
            showAvatar={false}
            size={0}
            className="post-detail-top-bar__nickname"
          />
          {createdAt ? (
            <span className="post-detail-top-bar__time">
              编辑于 {createdAt}
            </span>
          ) : null}
        </div>
      </div>

      {!isOwner ? (
        <FollowButton followed={followed} onClick={onFollow} />
      ) : null}

      <div className="post-detail-top-bar__actions">
        <Button
          type="text"
          size="small"
          icon={<ShareAltOutlined />}
          aria-label="分享"
          onClick={onShare}
        />
        {hasMore ? (
          <Dropdown menu={{ items: moreMenu }} trigger={['click']}>
            <Button
              type="text"
              size="small"
              icon={<MoreOutlined />}
              aria-label="更多"
            />
          </Dropdown>
        ) : null}
      </div>
    </header>
  );
};

export default memo(PostDetailTopBar);
