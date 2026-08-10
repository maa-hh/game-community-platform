import React from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { message } from 'antd';

import PostFeedList from '@/components/PostFeedList';
import { formatApiError } from '@/utils/apiError';
import { buildReturnNavigationState } from '@/utils/returnNavigation';

import { useHomeFeed } from './useHomeFeed';

import './style.less';

function Home() {
  const navigate = useNavigate();
  const location = useLocation();
  const {
    items,
    loading,
    loadingMore,
    hasMore,
    sentinelRef,
    reload,
    handleLike,
    handleFavorite,
  } = useHomeFeed();

  const handleRefresh = async () => {
    try {
      await reload();
    } catch {
      message.error(formatApiError('加载最新帖失败', new Error()));
    }
  };

  return (
    <div className="home-page">
      <PostFeedList
        items={items}
        loading={loading}
        emptyText="暂无帖子"
        layout="masonry"
        onRefresh={handleRefresh}
        onItemClick={(id) =>
          navigate(`/post/${id}`, {
            state: {
              ...buildReturnNavigationState(location),
            },
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

export default Home;
