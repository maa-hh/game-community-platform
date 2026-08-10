import React from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { Segmented } from 'antd';

import PostFeedList from '@/components/PostFeedList';

import { feedEmptyText, feedTypeTabs, type FeedTypeFilter } from './config';
import { useFeedPage } from './useFeedPage';
import { buildReturnNavigationState } from '@/utils/returnNavigation';

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
    hasMore,
    sentinelRef,
    loadFeed,
    handleLike,
    handleFavorite,
  } = useFeedPage(activeType);

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
        emptyText={feedEmptyText[activeType]}
        layout="masonry"
        onRefresh={() => void loadFeed()}
        onItemClick={(id) =>
          navigate(`/post/${id}`, {
            state: buildReturnNavigationState(location),
          })
        }
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
