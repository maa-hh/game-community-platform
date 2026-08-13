import React, { useCallback, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import {
  AutoComplete,
  Button,
  Empty,
  Input,
  Pagination,
  Segmented,
  Spin,
  Tabs,
  message,
} from 'antd';
import { PlusOutlined, SyncOutlined } from '@ant-design/icons';

import GameDiscoverFilters from '@/views/Games/parts/GameDiscoverFilters';
import GameCard from '@/views/Games/parts/GameCard';
import GameMasonryGrid from '@/views/Games/parts/GameMasonryGrid';
import GameSearchModal from '@/views/Games/parts/GameSearchModal';
import { useRequireLogin } from '@/hooks/useRequireLogin';
import {
  checkGameFollowBatchApi,
  followGameApi,
  unfollowGameApi,
} from '@/service/userGame';
import type {
  GameDiscoverBoard,
  IGameListItem,
  IUserGameItem,
} from '@/types/game';
import { formatApiError } from '@/utils/apiError';
import { buildReturnNavigationState } from '@/utils/returnNavigation';
import { searchGamesApi } from '@/service/game';
import { fetchSuggestApi, triggerSuggestApi } from '@/service/search';
import type { ISuggestItem } from '@/service/search';

import { useGamesPage, type GamesTabKey } from './useGamesPage';

import './style.less';

const TAB_ITEMS = [
  { key: 'mine', label: '我的游戏' },
  { key: 'discover', label: '发现' },
] as const;

const BOARD_OPTIONS = [
  { label: '全部', value: 'all' },
  { label: '国区热门', value: 'hot' },
  { label: '国区新品', value: 'new' },
  { label: '国区免费', value: 'free' },
  { label: '国区折扣', value: 'discount' },
] as const;

const GAME_SEARCH_SUGGEST_DEBOUNCE_MS = 280;
const GAME_SEARCH_SUGGEST_SIZE = 8;

function toListItem(game: IUserGameItem): IGameListItem {
  return {
    appId: game.appId,
    name: game.name,
    coverUrl: game.coverUrl,
    genres: game.genres,
    developer: game.developer,
    publisher: game.publisher,
    releaseDate: game.releaseDate,
    steamScore: game.steamScore,
    avgScore: game.avgScore,
    reviewCount: game.reviewCount,
    discussCount: game.discussCount,
  };
}

function Games() {
  const location = useLocation();
  const navigate = useNavigate();
  const { requireLogin, isLoggedIn } = useRequireLogin();
  const [searchOpen, setSearchOpen] = useState(false);
  const [followLoadingId, setFollowLoadingId] = useState<number | null>(null);
  const [followedMap, setFollowedMap] = useState<Record<number, boolean>>({});
  const [gameSearchText, setGameSearchText] = useState('');
  const [gameSearchKeyword, setGameSearchKeyword] = useState('');
  const [gameSearchItems, setGameSearchItems] = useState<IGameListItem[]>([]);
  const [gameSearchLoading, setGameSearchLoading] = useState(false);
  const [gameSearchSuggestions, setGameSearchSuggestions] = useState<
    ISuggestItem[]
  >([]);
  const [gameSearchSuggestLoading, setGameSearchSuggestLoading] =
    useState(false);
  const [gameSearchSuggestResolved, setGameSearchSuggestResolved] =
    useState(false);
  const [gameSearchPopupOpen, setGameSearchPopupOpen] = useState(false);
  const gameSearchRequestRef = React.useRef(0);
  const gameSearchSuggestDebounceRef = React.useRef<number | null>(null);

  const {
    activeTab,
    setActiveTab,
    myGames,
    myLoading,
    steamBound,
    importing,
    loadMyGames,
    importSteam,
    discoverItems,
    discoverLoading,
    discoverBoard,
    discoverSort,
    discoverOrder,
    discoverFilters,
    discoverPage,
    discoverTotal,
    pageSize,
    changeDiscoverBoard,
    changeDiscoverSort,
    changeDiscoverOrder,
    changeDiscoverFilters,
    changeDiscoverPage,
    reloadDiscover,
  } = useGamesPage();

  const openGame = (appId: number) => {
    navigate(`/game/${appId}`, {
      state: {
        ...buildReturnNavigationState(location),
      },
    });
  };

  const cancelGameSearchSuggestion = useCallback(() => {
    if (gameSearchSuggestDebounceRef.current != null) {
      window.clearTimeout(gameSearchSuggestDebounceRef.current);
      gameSearchSuggestDebounceRef.current = null;
    }
  }, []);

  const clearGameSearch = useCallback(() => {
    cancelGameSearchSuggestion();
    gameSearchRequestRef.current += 1;
    setGameSearchText('');
    setGameSearchKeyword('');
    setGameSearchItems([]);
    setGameSearchLoading(false);
    setGameSearchSuggestions([]);
    setGameSearchSuggestLoading(false);
    setGameSearchSuggestResolved(false);
    setGameSearchPopupOpen(false);
  }, [cancelGameSearchSuggestion]);

  const loadGameSearchSuggestions = useCallback(
    (value: string) => {
      cancelGameSearchSuggestion();
      const keyword = value.trim();
      if (!keyword) {
        gameSearchRequestRef.current += 1;
        setGameSearchSuggestions([]);
        setGameSearchSuggestLoading(false);
        setGameSearchSuggestResolved(false);
        setGameSearchPopupOpen(false);
        return;
      }

      const requestId = ++gameSearchRequestRef.current;
      setGameSearchPopupOpen(true);
      setGameSearchSuggestions([]);
      setGameSearchSuggestLoading(true);
      setGameSearchSuggestResolved(false);
      gameSearchSuggestDebounceRef.current = window.setTimeout(() => {
        void fetchSuggestApi(keyword, 'GAME')
          .then((res) => {
            if (requestId !== gameSearchRequestRef.current) return;
            setGameSearchSuggestions(res.data || []);
            setGameSearchSuggestResolved(true);
          })
          .catch(() => {
            if (requestId !== gameSearchRequestRef.current) return;
            setGameSearchSuggestions([]);
            setGameSearchSuggestResolved(true);
          })
          .finally(() => {
            if (requestId === gameSearchRequestRef.current) {
              setGameSearchSuggestLoading(false);
            }
          });
      }, GAME_SEARCH_SUGGEST_DEBOUNCE_MS);
    },
    [cancelGameSearchSuggestion],
  );

  React.useEffect(
    () => () => cancelGameSearchSuggestion(),
    [cancelGameSearchSuggestion],
  );

  const submitGameSearch = useCallback(
    async (value: string) => {
      const keyword = value.trim();
      if (!keyword) {
        clearGameSearch();
        return;
      }

      const requestId = ++gameSearchRequestRef.current;
      cancelGameSearchSuggestion();
      setGameSearchKeyword(keyword);
      setGameSearchLoading(true);
      setGameSearchSuggestions([]);
      setGameSearchSuggestLoading(false);
      setGameSearchSuggestResolved(false);
      setGameSearchPopupOpen(false);
      try {
        const res = await searchGamesApi(keyword, { page: 1, size: pageSize });
        if (requestId !== gameSearchRequestRef.current) return;
        setGameSearchItems(res.data || []);
      } catch (err) {
        if (requestId === gameSearchRequestRef.current) {
          setGameSearchItems([]);
          message.error(formatApiError('游戏搜索失败', err));
        }
      } finally {
        if (requestId === gameSearchRequestRef.current) {
          setGameSearchLoading(false);
        }
      }
    },
    [cancelGameSearchSuggestion, clearGameSearch, pageSize],
  );

  const handleDiscoverBoardChange = useCallback(
    (board: GameDiscoverBoard) => {
      clearGameSearch();
      changeDiscoverBoard(board);
    },
    [changeDiscoverBoard, clearGameSearch],
  );

  const gameSearchActive =
    discoverBoard === 'all' && gameSearchKeyword.length > 0;
  const displayDiscoverItems = gameSearchActive
    ? gameSearchItems
    : discoverItems;
  const displayDiscoverLoading = gameSearchActive
    ? gameSearchLoading
    : discoverLoading;

  const ensureDiscoverFollowState = useCallback(
    async (items: IGameListItem[]) => {
      if (!isLoggedIn) {
        setFollowedMap({});
        return;
      }
      try {
        const res = await checkGameFollowBatchApi(
          items.map((item) => item.appId),
        );
        const followed = res.data || {};
        setFollowedMap(
          Object.fromEntries(
            items.map((item) => [item.appId, Boolean(followed[item.appId])]),
          ),
        );
      } catch {
        setFollowedMap({});
      }
    },
    [isLoggedIn],
  );

  React.useEffect(() => {
    if (activeTab === 'discover' && displayDiscoverItems.length > 0) {
      void ensureDiscoverFollowState(displayDiscoverItems);
    }
  }, [activeTab, displayDiscoverItems, ensureDiscoverFollowState]);

  const toggleDiscoverFollow = async (game: IGameListItem) => {
    if (!requireLogin()) return;
    setFollowLoadingId(game.appId);
    try {
      if (followedMap[game.appId]) {
        await unfollowGameApi(game.appId);
        setFollowedMap((prev) => ({ ...prev, [game.appId]: false }));
        message.success('已移出我的游戏');
      } else {
        await followGameApi(game.appId, 'discover');
        setFollowedMap((prev) => ({ ...prev, [game.appId]: true }));
        message.success('已加入我的游戏');
      }
    } catch (err) {
      message.error(formatApiError('操作失败', err));
    } finally {
      setFollowLoadingId(null);
    }
  };

  const renderMine = () => {
    if (!isLoggedIn) {
      return (
        <Empty description="登录后查看和管理我的游戏">
          <Button type="primary" onClick={() => requireLogin()}>
            去登录
          </Button>
        </Empty>
      );
    }

    if (myLoading) {
      return (
        <div className="games-page__loading">
          <Spin />
        </div>
      );
    }

    return (
      <>
        <div className="games-page__toolbar">
          <div className="games-page__toolbar-left">
            {steamBound ? (
              <Button
                icon={<SyncOutlined spin={importing} />}
                loading={importing}
                onClick={() => void importSteam()}
              >
                从 Steam 导入
              </Button>
            ) : (
              <span className="games-page__hint">
                绑定 Steam 后可一键导入游戏库
              </span>
            )}
          </div>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => setSearchOpen(true)}
          >
            搜索添加
          </Button>
        </div>
        {myGames.length === 0 ? (
          <Empty description="还没有游戏，搜索添加或从 Steam 导入">
            <Button type="primary" onClick={() => setSearchOpen(true)}>
              搜索添加游戏
            </Button>
          </Empty>
        ) : (
          <GameMasonryGrid>
            {myGames.map((game) => (
              <GameCard
                key={game.appId}
                game={toListItem(game)}
                onClick={() => openGame(game.appId)}
              />
            ))}
          </GameMasonryGrid>
        )}
      </>
    );
  };

  const renderDiscover = () => (
    <>
      <div className="games-page__toolbar games-page__toolbar--discover">
        <Segmented
          options={BOARD_OPTIONS.map((item) => ({
            label: item.label,
            value: item.value,
          }))}
          value={discoverBoard}
          onChange={(value) =>
            handleDiscoverBoardChange(value as GameDiscoverBoard)
          }
        />
        {discoverBoard === 'all' ? (
          <AutoComplete
            className="games-page__discover-search"
            options={gameSearchSuggestions
              .slice(0, GAME_SEARCH_SUGGEST_SIZE)
              .map((item) => ({
                value: item.term,
                label: item.term,
              }))}
            open={gameSearchPopupOpen && gameSearchText.trim().length > 0}
            filterOption={false}
            notFoundContent={
              gameSearchSuggestLoading
                ? '正在搜索游戏…'
                : gameSearchSuggestResolved
                  ? '暂无匹配游戏，请尝试其他关键词'
                  : null
            }
            onChange={(value) => {
              setGameSearchText(value);
              if (!value.trim()) {
                clearGameSearch();
                return;
              }
              if (value.trim() !== gameSearchKeyword) {
                setGameSearchKeyword('');
                setGameSearchItems([]);
              }
              loadGameSearchSuggestions(value);
            }}
            onFocus={() => {
              if (gameSearchText.trim()) setGameSearchPopupOpen(true);
            }}
            onOpenChange={setGameSearchPopupOpen}
            onSelect={(value) => {
              setGameSearchText(value);
              setGameSearchPopupOpen(false);
              if (isLoggedIn) {
                void triggerSuggestApi({ term: value }).catch(() => undefined);
              }
              void submitGameSearch(value);
            }}
          >
            <Input.Search
              value={gameSearchText}
              allowClear
              loading={gameSearchLoading || gameSearchSuggestLoading}
              placeholder="搜索感兴趣的游戏"
              onSearch={(value) => void submitGameSearch(value)}
            />
          </AutoComplete>
        ) : null}
      </div>
      <GameDiscoverFilters
        board={discoverBoard}
        sort={discoverSort}
        order={discoverOrder}
        filters={discoverFilters}
        onSortChange={changeDiscoverSort}
        onOrderChange={changeDiscoverOrder}
        onFiltersChange={changeDiscoverFilters}
      />
      {displayDiscoverLoading ? (
        <div className="games-page__loading">
          <Spin />
        </div>
      ) : displayDiscoverItems.length === 0 ? (
        <Empty
          description={
            gameSearchActive ? '未找到相关游戏' : '暂无符合条件的游戏'
          }
        />
      ) : (
        <>
          <GameMasonryGrid>
            {displayDiscoverItems.map((game) => (
              <GameCard
                key={game.appId}
                game={game}
                showFollow
                followed={followedMap[game.appId]}
                followLoading={followLoadingId === game.appId}
                onClick={() => openGame(game.appId)}
                onFollow={() => void toggleDiscoverFollow(game)}
              />
            ))}
          </GameMasonryGrid>
          {!gameSearchActive && discoverTotal > pageSize ? (
            <Pagination
              className="games-page__pager"
              current={discoverPage}
              pageSize={pageSize}
              total={discoverTotal}
              onChange={changeDiscoverPage}
              showSizeChanger={false}
            />
          ) : null}
        </>
      )}
    </>
  );

  return (
    <div className="games-page">
      <Tabs
        activeKey={activeTab}
        onChange={(key) => setActiveTab(key as GamesTabKey)}
        items={TAB_ITEMS.map((item) => ({ key: item.key, label: item.label }))}
        className="games-page__tabs"
        size="large"
      />

      <section className="games-page__panel">
        {activeTab === 'mine' ? renderMine() : renderDiscover()}
      </section>

      <GameSearchModal
        open={searchOpen}
        onClose={() => setSearchOpen(false)}
        onAdded={() => {
          void loadMyGames();
          void reloadDiscover();
        }}
      />
    </div>
  );
}

export default Games;
