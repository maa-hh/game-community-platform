import React, { useCallback, useEffect, useState } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { Empty, Spin, Tabs } from 'antd';

import ListEndHint from '@/base-ui/ListEndHint';
import UserAvatarWithFrame from '@/base-ui/UserAvatarWithFrame';
import PostFeedList from '@/components/PostFeedList';
import GameCard from '@/views/Games/parts/GameCard';
import GameMasonryGrid from '@/views/Games/parts/GameMasonryGrid';
import {
  useSearchArticles,
  useSearchGames,
  useSearchUsers,
} from '@/hooks/usePagedSocial';
import {
  buildGameDetailNavigationState,
  buildPostDetailNavigationState,
} from '@/utils/detailNavigation';
import type { LatestPostItem } from '@/types/post';

import './style.less';

type SearchTab = 'all' | 'posts' | 'games' | 'users';

function Search() {
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const keyword = (searchParams.get('q') || '').trim();
  const tabParam = searchParams.get('tab') as SearchTab | null;
  const [activeTab, setActiveTab] = useState<SearchTab>(
    tabParam === 'posts' ||
      tabParam === 'games' ||
      tabParam === 'users' ||
      tabParam === 'all'
      ? tabParam
      : 'all',
  );

  useEffect(() => {
    const next = searchParams.get('tab');
    if (
      next === 'posts' ||
      next === 'games' ||
      next === 'users' ||
      next === 'all'
    ) {
      setActiveTab(next);
    } else {
      setActiveTab('all');
    }
  }, [searchParams]);

  const usersEnabled =
    Boolean(keyword) && (activeTab === 'users' || activeTab === 'all');
  const postsEnabled =
    Boolean(keyword) && (activeTab === 'posts' || activeTab === 'all');
  const gamesEnabled =
    Boolean(keyword) && (activeTab === 'games' || activeTab === 'all');

  const {
    items: users,
    loading: usersLoading,
    loadingMore: usersLoadingMore,
    hasMore: usersHasMore,
    sentinelRef: usersSentinelRef,
    total: usersTotal,
  } = useSearchUsers(keyword, usersEnabled);

  const {
    items: posts,
    loading: postsLoading,
    loadingMore: postsLoadingMore,
    hasMore: postsHasMore,
    sentinelRef: postsSentinelRef,
    total: postsTotal,
  } = useSearchArticles(keyword, postsEnabled);

  const {
    items: games,
    loading: gamesLoading,
    loadingMore: gamesLoadingMore,
    hasMore: gamesHasMore,
    sentinelRef: gamesSentinelRef,
    total: gamesTotal,
  } = useSearchGames(keyword, gamesEnabled);

  const handleTabChange = (key: string) => {
    const next = key as SearchTab;
    setActiveTab(next);
    const nextParams = new URLSearchParams(searchParams);
    nextParams.set('tab', next);
    if (keyword) nextParams.set('q', keyword);
    setSearchParams(nextParams, {
      replace: true,
      preventScrollReset: true,
    });
  };

  const handlePostClick = useCallback(
    (item: LatestPostItem) => {
      navigate(`/post/${item.id}`, {
        state: buildPostDetailNavigationState(location, item),
      });
    },
    [location, navigate],
  );

  const renderUsers = () => {
    if (!keyword) {
      return <Empty description="输入关键词开始搜索" />;
    }
    if (usersLoading && users.length === 0) {
      return (
        <div className="search-page__loading">
          <Spin />
        </div>
      );
    }
    if (!usersLoading && users.length === 0) {
      return <Empty description="未找到相关用户" />;
    }
    return (
      <>
        <div className="search-page__user-list">
          {users.map((item) => (
            <button
              key={item.accountId}
              type="button"
              className="search-page__user-item"
              onClick={() => navigate(`/profile?accountId=${item.accountId}`)}
            >
              <UserAvatarWithFrame
                accountId={item.accountId}
                name={item.username || 'U'}
                src={item.avatar}
                size={48}
              />
              <span className="search-page__user-copy">
                <strong>{item.username}</strong>
                <span>
                  ID {item.accountId ?? '—'}
                  {item.signature ? ` · ${item.signature}` : ''}
                </span>
              </span>
            </button>
          ))}
        </div>
        <ListEndHint
          ref={usersSentinelRef}
          loadingMore={usersLoadingMore}
          hasMore={usersHasMore}
          itemCount={users.length}
          endText={
            usersTotal > 0
              ? `已经翻到底了哦（共 ${usersTotal} 条）`
              : '已经翻到底了哦'
          }
        />
      </>
    );
  };

  const renderPosts = (options?: { sectionTitle?: boolean }) => {
    if (!keyword) {
      return <Empty description="输入关键词开始搜索" />;
    }

    const list = (
      <PostFeedList
        items={posts}
        loading={postsLoading}
        layout="masonry"
        emptyText="未找到相关帖子"
        onItemClick={handlePostClick}
        infinite={{
          sentinelRef: postsSentinelRef,
          loadingMore: postsLoadingMore,
          hasMore: postsHasMore,
          itemCount: posts.length,
        }}
      />
    );

    if (options?.sectionTitle) {
      return (
        <div className="search-page__section">
          <h2 className="search-page__section-title">
            帖子{postsTotal > 0 ? `（${postsTotal}）` : ''}
          </h2>
          {list}
        </div>
      );
    }

    return list;
  };

  const renderGames = (options?: { sectionTitle?: boolean }) => {
    if (!keyword) {
      return <Empty description="输入关键词开始搜索" />;
    }
    if (gamesLoading && games.length === 0) {
      return (
        <div className="search-page__loading">
          <Spin />
        </div>
      );
    }
    if (!gamesLoading && games.length === 0) {
      return <Empty description="未找到相关游戏" />;
    }

    const list = (
      <>
        <GameMasonryGrid>
          {games.map((game) => (
            <GameCard
              key={game.appId}
              game={game}
              onClick={() =>
                navigate(`/game/${game.appId}`, {
                  state: buildGameDetailNavigationState(location, game),
                })
              }
            />
          ))}
        </GameMasonryGrid>
        <ListEndHint
          ref={gamesSentinelRef}
          loadingMore={gamesLoadingMore}
          hasMore={gamesHasMore}
          itemCount={games.length}
          endText={
            gamesTotal > 0
              ? `已经翻到底了哦（共 ${gamesTotal} 条）`
              : '已经翻到底了哦'
          }
        />
      </>
    );

    if (options?.sectionTitle) {
      return (
        <div className="search-page__section">
          <h2 className="search-page__section-title">
            游戏{gamesTotal > 0 ? `（${gamesTotal}）` : ''}
          </h2>
          {list}
        </div>
      );
    }
    return list;
  };

  return (
    <div className="search-page">
      <div className="search-page__inner">
        <Tabs
          activeKey={activeTab}
          onChange={handleTabChange}
          items={[
            { key: 'all', label: '综合' },
            { key: 'posts', label: '帖子' },
            { key: 'games', label: '游戏' },
            { key: 'users', label: '用户' },
          ]}
        />

        {activeTab === 'posts' && renderPosts()}
        {activeTab === 'games' && renderGames()}

        {activeTab === 'all' && (
          <div className="search-page__section">
            <h2 className="search-page__section-title">
              用户{usersTotal > 0 ? `（${usersTotal}）` : ''}
            </h2>
            {renderUsers()}
            {renderGames({ sectionTitle: true })}
            {renderPosts({ sectionTitle: true })}
          </div>
        )}

        {activeTab === 'users' && renderUsers()}
      </div>
    </div>
  );
}

export default Search;
