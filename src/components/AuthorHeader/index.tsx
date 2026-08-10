import React, { memo } from 'react';
import type { FC } from 'react';
import { Button, Dropdown, Space } from 'antd';
import { MoreOutlined } from '@ant-design/icons';

import ProfileUserLink from '@/components/ProfileUserLink';
import FollowButton from '@/components/FollowButton';

import type { AuthorHeaderProps } from './types';

import './style.less';

/** 页内作者行：头像 · 昵称 · 时间 · 关注 · 更多（差异仅数据） */
const AuthorHeader: FC<AuthorHeaderProps> = ({
  author,
  time,
  followed,
  isOwner,
  sentinelRef,
  extra,
  onFollow,
  moreMenu,
  className,
}) => {
  return (
    <header
      ref={sentinelRef}
      className={`author-header${className ? ` ${className}` : ''}`}
    >
      <div className="author-header__main">
        <ProfileUserLink
          accountId={author.accountId}
          nickname={author.nickname}
          avatar={author.avatar}
          size={40}
          showNickname={false}
          className="author-header__avatar"
        />
        <div className="author-header__text">
          <ProfileUserLink
            accountId={author.accountId}
            nickname={author.nickname}
            avatar={author.avatar}
            showAvatar={false}
            size={0}
            className="author-header__nickname"
          />
          {time ? <div className="author-header__time">{time}</div> : null}
        </div>
      </div>
      <Space className="author-header__actions" size={8}>
        {extra}
        {!isOwner && onFollow && (
          <FollowButton followed={followed} onClick={onFollow} />
        )}
        {moreMenu && moreMenu.length > 0 && (
          <Dropdown menu={{ items: moreMenu }} trigger={['click']}>
            <Button size="small" type="text" icon={<MoreOutlined />} />
          </Dropdown>
        )}
      </Space>
    </header>
  );
};

export default memo(AuthorHeader);

export type { AuthorHeaderProps } from './types';
