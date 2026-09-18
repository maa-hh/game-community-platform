import React from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { App, DatePicker, Segmented, Select } from 'antd';
import type { Dayjs } from 'dayjs';

import PostFeedList from '@/components/PostFeedList';
import { formatApiError } from '@/utils/apiError';
import type { HotRankBoard } from '@/service/hotRank';

import { formatHotRankPeriodLabel } from '@/utils/hotRankPeriod';

import {
  hotRankBoardTabs,
  hotRankEmptyText,
  ALL_CATEGORY_VALUE,
} from './config';
import { useHotRankPage } from './useHotRankPage';
import { buildPostDetailNavigationState } from '@/utils/detailNavigation';

import './style.less';

function Recommend() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const location = useLocation();
  const {
    board,
    categoryId,
    periodDate,
    categories,
    items,
    loading,
    refreshing,
    onBoardChange,
    onCategoryChange,
    onPeriodChange,
    loadRank,
    handleLike,
  } = useHotRankPage();

  const handleRefresh = async () => {
    try {
      await loadRank({ refresh: board === 'daily' });
    } catch (err) {
      message.error(formatApiError('刷新热榜失败', err));
    }
  };

  const categoryOptions = [
    { label: '全部', value: ALL_CATEGORY_VALUE },
    ...categories.map((item) => ({
      label: item.name,
      value: item.id,
    })),
  ];

  return (
    <div className="recommend-page">
      <div className="recommend-page__toolbar">
        <Segmented
          block
          className="recommend-page__tabs"
          value={board}
          options={hotRankBoardTabs}
          onChange={(value) => onBoardChange(value as HotRankBoard)}
        />
        <div className="recommend-page__filters">
          <Select
            className="recommend-page__category"
            value={categoryId ?? ALL_CATEGORY_VALUE}
            options={categoryOptions}
            onChange={(value) =>
              onCategoryChange(value === ALL_CATEGORY_VALUE ? undefined : value)
            }
          />
          <DatePicker
            className="recommend-page__period"
            value={periodDate}
            onChange={(value) => onPeriodChange(value as Dayjs | null)}
            picker={board === 'weekly' ? 'week' : 'date'}
            format={(value) => formatHotRankPeriodLabel(board, value)}
            allowClear={false}
          />
        </div>
      </div>

      <PostFeedList
        items={items}
        loading={loading}
        refreshing={refreshing}
        emptyText={hotRankEmptyText[board]}
        layout="hotRank"
        onRefresh={handleRefresh}
        onItemClick={(item) =>
          navigate(`/post/${item.id}`, {
            state: buildPostDetailNavigationState(location, item),
          })
        }
        onLikeClick={handleLike}
        showRank
      />
    </div>
  );
}

export default Recommend;
