import React, { useCallback } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { Segmented } from 'antd';

import PostFeedList from '@/components/PostFeedList';
import type { LatestPostItem } from '@/types/post';

import { feedEmptyText, feedTypeTabs, type FeedTypeFilter } from './config';
import { useFeedPage } from './useFeedPage';
import { buildPostDetailNavigationState } from '@/utils/detailNavigation';

import './style.less';

function Feed() {
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const tabParam = searchParams.get('tab') as FeedTypeFilter | null;
  const activeType =
    tabParam && feedTypeTabs.some((tab) => tab.value === tabParam)
      ? tabParam
      : 'all';
  const {
    items,
    loading,
    loadingMore,
    refreshing,
    hasMore,
    sentinelRef,
    loadFeed,
    handleLike,
    handleFavorite,
  } = useFeedPage(activeType);
  const handleItemClick = useCallback(
    (item: LatestPostItem) => {
      navigate(`/post/${item.id}`, {
        state: buildPostDetailNavigationState(location, item),
      });
    },
    [location, navigate],
  );

  return (
    <div className="feed-page">
      <Segmented
        block
        className="feed-page__tabs"
        value={activeType}
        options={feedTypeTabs}
        onChange={(value) => {
          const next = new URLSearchParams(searchParams);
          if (value === 'all') next.delete('tab');
          else next.set('tab', String(value));
          setSearchParams(next, {
            replace: true,
            preventScrollReset: true,
          });
        }}
      />
      <PostFeedList
        items={items}
        loading={loading}
        refreshing={refreshing}
        emptyText={feedEmptyText[activeType]}
        layout="masonry"
        onRefresh={loadFeed}
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

export default Feed;
