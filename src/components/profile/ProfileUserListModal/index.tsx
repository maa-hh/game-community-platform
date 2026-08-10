import React, { memo } from 'react';
import type { FC } from 'react';
import { Alert, List, Modal, Spin } from 'antd';
import { useNavigate } from 'react-router-dom';

import ListEndHint from '@/base-ui/ListEndHint';
import UserAvatarWithFrame from '@/base-ui/UserAvatarWithFrame';
import { usePageList } from '@/hooks/usePageList';
import { fetchFollowersListApi, fetchFollowingListApi } from '@/service/social';
import type { IUserCard } from '@/service/types';

import './style.less';

export type ProfileUserListType = 'following' | 'followers';

interface IProps {
  open: boolean;
  type: ProfileUserListType;
  onClose: () => void;
}

const TITLE_MAP: Record<ProfileUserListType, string> = {
  following: '关注',
  followers: '粉丝',
};

function buildProfileHref(item: IUserCard): string | null {
  if (item.accountId > 0) {
    return `/profile?accountId=${item.accountId}`;
  }
  return null;
}

const ProfileUserListModal: FC<IProps> = ({ open, type, onClose }) => {
  const navigate = useNavigate();
  const pager = usePageList<IUserCard>({
    pageSize: 20,
    enabled: open,
    resetDeps: [type, open],
    fetchPage: (page, size) =>
      type === 'following'
        ? fetchFollowingListApi(page, size)
        : fetchFollowersListApi(page, size),
  });

  const showEmpty =
    !pager.loading && pager.items.length === 0 && !pager.loadingMore;

  return (
    <Modal
      open={open}
      title={TITLE_MAP[type]}
      footer={null}
      onCancel={onClose}
      destroyOnHidden
      className="profile-user-list-modal"
      width={480}
    >
      {pager.loading && pager.items.length === 0 ? (
        <div className="profile-user-list-modal__loading">
          <Spin />
        </div>
      ) : null}

      {showEmpty ? (
        <Alert
          type="info"
          showIcon
          message={`暂无${TITLE_MAP[type]}`}
          className="profile-user-list-modal__empty"
        />
      ) : (
        <List
          dataSource={pager.items}
          renderItem={(item) => {
            const href = buildProfileHref(item);
            const displayId = item.accountId;
            return (
              <List.Item
                className={`profile-user-list-modal__item${
                  href ? ' is-clickable' : ''
                }`}
                onClick={() => {
                  if (!href) return;
                  navigate(href);
                  onClose();
                }}
              >
                <List.Item.Meta
                  avatar={
                    <UserAvatarWithFrame
                      accountId={item.accountId}
                      name={item.username || 'U'}
                      src={item.avatar}
                      size={48}
                    />
                  }
                  title={item.username}
                  description={
                    <span>
                      ID {displayId ?? '—'}
                      {item.signature ? ` · ${item.signature}` : ''}
                    </span>
                  }
                />
              </List.Item>
            );
          }}
        />
      )}

      <ListEndHint
        ref={pager.sentinelRef}
        loadingMore={pager.loadingMore}
        hasMore={pager.hasMore}
        itemCount={pager.items.length}
      />
    </Modal>
  );
};

export default memo(ProfileUserListModal);
