import React, { memo, useEffect, useRef } from 'react';
import type { FC } from 'react';
import { Alert, Modal, Spin } from 'antd';
import { useNavigate } from 'react-router-dom';

import ListEndHint from '@/base-ui/ListEndHint';
import UserAvatarWithFrame from '@/base-ui/UserAvatarWithFrame';
import { usePageList } from '@/hooks/usePageList';
import { useAppSelector } from '@/store';
import { fetchFollowersListApi, fetchFollowingListApi } from '@/service/social';
import type { IUserCard } from '@/service/types';

import './style.less';

export type ProfileUserListType = 'following' | 'followers';

interface IProps {
  open: boolean;
  type: ProfileUserListType;
  onClose: () => void;
  onRefreshReady?: (refresh: (() => Promise<void>) | null) => void;
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

const ProfileUserListModal: FC<IProps> = ({
  open,
  type,
  onClose,
  onRefreshReady,
}) => {
  const navigate = useNavigate();
  const accountId = useAppSelector((state) => state.auth.user?.accountId);
  const pager = usePageList<IUserCard>({
    pageSize: 20,
    enabled: open,
    cacheKey: `profile-users:${type}`,
    resetDeps: [type],
    fetchPage: (page, size) =>
      type === 'following'
        ? fetchFollowingListApi(page, size)
        : fetchFollowersListApi(page, size),
  });
  const reloadProfileUsers = pager.reload;
  const wasOpenRef = useRef(false);
  const previousTypeRef = useRef<ProfileUserListType | null>(null);

  useEffect(() => {
    onRefreshReady?.(reloadProfileUsers);
    return () => onRefreshReady?.(null);
  }, [onRefreshReady, reloadProfileUsers]);

  useEffect(() => {
    if (!open || !accountId) {
      wasOpenRef.current = false;
      previousTypeRef.current = null;
      return;
    }

    // 关注列表可能在上次打开时被缓存；每次重新打开都以服务端结果为准。
    const shouldReload =
      !wasOpenRef.current || previousTypeRef.current !== type;
    wasOpenRef.current = true;
    previousTypeRef.current = type;
    if (!shouldReload) return;

    void reloadProfileUsers().catch(() => undefined);
  }, [accountId, open, reloadProfileUsers, type]);

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
          title={`暂无${TITLE_MAP[type]}`}
          className="profile-user-list-modal__empty"
        />
      ) : (
        <div className="profile-user-list-modal__list">
          {pager.items.map((item) => {
            const href = buildProfileHref(item);
            const displayId = item.accountId;
            return (
              <button
                key={item.accountId}
                type="button"
                className={`profile-user-list-modal__item${
                  href ? ' is-clickable' : ''
                }`}
                onClick={() => {
                  if (!href) return;
                  navigate(href);
                  onClose();
                }}
              >
                <UserAvatarWithFrame
                  accountId={item.accountId}
                  name={item.username || 'U'}
                  src={item.avatar}
                  size={48}
                />
                <span className="profile-user-list-modal__copy">
                  <strong>{item.username}</strong>
                  <span>
                    ID {displayId ?? '—'}
                    {item.signature ? ` · ${item.signature}` : ''}
                  </span>
                </span>
              </button>
            );
          })}
        </div>
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
