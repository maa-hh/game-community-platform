import React, { useCallback, useEffect, useState } from 'react';
import { App, Empty, Input, Modal, Spin } from 'antd';
import { SearchOutlined } from '@ant-design/icons';

import GameCard from '@/views/Games/parts/GameCard';
import GameMasonryGrid from '@/views/Games/parts/GameMasonryGrid';
import { searchGamesApi } from '@/service/game';
import {
  checkGameFollowBatchApi,
  followGameApi,
  unfollowGameApi,
} from '@/service/userGame';
import type { IGameListItem } from '@/types/game';
import { formatApiError } from '@/utils/apiError';
import { useRequireLogin } from '@/hooks/useRequireLogin';

interface IGameSearchModalProps {
  open: boolean;
  onClose: () => void;
  onChanged?: () => void;
}

function GameSearchModal({ open, onClose, onChanged }: IGameSearchModalProps) {
  const { message } = App.useApp();
  const { requireLogin } = useRequireLogin();
  const [keyword, setKeyword] = useState('');
  const [loading, setLoading] = useState(false);
  const [results, setResults] = useState<IGameListItem[]>([]);
  const [followedMap, setFollowedMap] = useState<Record<number, boolean>>({});
  const [followLoadingId, setFollowLoadingId] = useState<number | null>(null);

  useEffect(() => {
    if (!open) {
      setKeyword('');
      setResults([]);
      setFollowedMap({});
    }
  }, [open]);

  const runSearch = useCallback(async () => {
    const q = keyword.trim();
    if (!q) {
      message.warning('请输入游戏名');
      return;
    }
    setLoading(true);
    try {
      const res = await searchGamesApi(q, { page: 1, size: 20 });
      const list = res.data || [];
      setResults(list);
      if (list.length === 0) {
        return;
      }
      try {
        const checkRes = await checkGameFollowBatchApi(
          list.map((item) => item.appId),
        );
        const followed = checkRes.data || {};
        setFollowedMap(
          Object.fromEntries(
            list.map((item) => [item.appId, Boolean(followed[item.appId])]),
          ),
        );
      } catch {
        setFollowedMap({});
      }
    } catch (err) {
      message.error(formatApiError('搜索失败', err));
    } finally {
      setLoading(false);
    }
  }, [keyword, message]);

  const toggleFollow = async (game: IGameListItem) => {
    if (!requireLogin()) return;
    setFollowLoadingId(game.appId);
    try {
      if (followedMap[game.appId]) {
        await unfollowGameApi(game.appId);
        setFollowedMap((prev) => ({ ...prev, [game.appId]: false }));
        message.success('已移出我的游戏');
      } else {
        await followGameApi(game.appId, 'manual');
        setFollowedMap((prev) => ({ ...prev, [game.appId]: true }));
        message.success('已加入我的游戏');
      }
      onChanged?.();
    } catch (err) {
      message.error(formatApiError('操作失败', err));
    } finally {
      setFollowLoadingId(null);
    }
  };

  return (
    <Modal
      title="搜索添加游戏"
      open={open}
      onCancel={onClose}
      footer={null}
      width={720}
      destroyOnHidden
    >
      <Input.Search
        placeholder="输入游戏名搜索"
        enterButton={<SearchOutlined />}
        value={keyword}
        onChange={(event) => setKeyword(event.target.value)}
        onSearch={() => void runSearch()}
        loading={loading}
        allowClear
      />
      <div className="games-page__search-results">
        {loading ? (
          <div className="games-page__loading">
            <Spin />
          </div>
        ) : results.length === 0 ? (
          <Empty description="搜索后可添加游戏到我的游戏" />
        ) : (
          <GameMasonryGrid className="games-page__waterfall--modal masonry-grid--scroll">
            {results.map((game) => (
              <GameCard
                key={game.appId}
                game={game}
                showFollow
                followed={followedMap[game.appId]}
                followLoading={followLoadingId === game.appId}
                onFollow={() => void toggleFollow(game)}
              />
            ))}
          </GameMasonryGrid>
        )}
      </div>
    </Modal>
  );
}

export default GameSearchModal;
