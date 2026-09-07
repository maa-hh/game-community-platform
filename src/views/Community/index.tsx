import React, { useCallback } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { message } from 'antd';

import PostFeedList from '@/components/PostFeedList';
import type { LatestPostItem } from '@/types/post';
import { formatApiError } from '@/utils/apiError';
import { buildPostDetailNavigationState } from '@/utils/detailNavigation';

import { useCommunityFeed } from './useCommunityFeed';

import './style.less';

function Community() {
  const navigate = useNavigate();
  const location = useLocation();
  const {
    items,
    loading,
    loadingMore,
    refreshing,
    hasMore,
    sentinelRef,
    reload,
    handleLike,
    handleFavorite,
  } = useCommunityFeed();

  const handleRefresh = useCallback(async () => {
    try {
      await reload();
    } catch (error) {
      message.error(formatApiError('加载最新帖失败', error));
    }
  }, [reload]);
  const handleItemClick = useCallback(
    (item: LatestPostItem) => {
      navigate(`/post/${item.id}`, {
        state: buildPostDetailNavigationState(location, item),
      });
    },
    [location, navigate],
  );

  return (
    <div className="community-page">
      <PostFeedList
        items={items}
        loading={loading}
        refreshing={refreshing}
        emptyText="暂无帖子"
        layout="masonry"
        onRefresh={handleRefresh}
        onItemClick={handleItemClick}
        onLikeClick={handleLike}
        onFavoriteClick={handleFavorite}
        infinite={{
          sentinelRef,
          loadingMore,
          hasMore,
          itemCount: items.length,
        }}
      />
    </div>
  );
}

export default Community;
