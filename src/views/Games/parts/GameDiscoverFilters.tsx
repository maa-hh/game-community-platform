import React, { memo, useMemo } from 'react';
import type { FC } from 'react';
import { Button, Checkbox, InputNumber, Popover, Select, Space } from 'antd';
import { FilterOutlined } from '@ant-design/icons';

import type {
  GameDiscoverBoard,
  GameDiscoverOrder,
  GameDiscoverSort,
  IGameDiscoverQuery,
} from '@/types/game';

import './GameDiscoverFilters.less';

export interface GameDiscoverFiltersProps {
  board: GameDiscoverBoard;
  sort: GameDiscoverSort;
  order: GameDiscoverOrder;
  filters: Omit<
    IGameDiscoverQuery,
    'board' | 'sort' | 'order' | 'page' | 'size'
  >;
  onSortChange: (sort: GameDiscoverSort) => void;
  onOrderChange: (order: GameDiscoverOrder) => void;
  onFiltersChange: (
    filters: Omit<
      IGameDiscoverQuery,
      'board' | 'sort' | 'order' | 'page' | 'size'
    >,
  ) => void;
}

const SORT_OPTIONS: { label: string; value: GameDiscoverSort }[] = [
  { label: 'Steam 评分', value: 'steam_score' },
  { label: '评价人数', value: 'steam_reviews' },
  { label: '原价', value: 'price' },
  { label: '现价', value: 'discount_price' },
];

const ORDER_OPTIONS: { label: string; value: GameDiscoverOrder }[] = [
  { label: '从高到低', value: 'desc' },
  { label: '从低到高', value: 'asc' },
];

const RANK_ORDER_OPTIONS: { label: string; value: GameDiscoverOrder }[] = [
  { label: '排名靠前', value: 'asc' },
  { label: '排名靠后', value: 'desc' },
];

function yuanToCent(value?: number | null): number | undefined {
  if (value == null || !Number.isFinite(value)) return undefined;
  return Math.round(value * 100);
}

function centToYuan(value?: number): number | undefined {
  if (value == null || !Number.isFinite(value)) return undefined;
  return value / 100;
}

const GameDiscoverFilters: FC<GameDiscoverFiltersProps> = ({
  board,
  sort,
  order,
  filters,
  onSortChange,
  onOrderChange,
  onFiltersChange,
}) => {
  const sortOptions = useMemo(() => {
    if (board === 'all') {
      return SORT_OPTIONS;
    }
    return [{ label: '榜单排名', value: 'rank' as const }, ...SORT_OPTIONS];
  }, [board]);

  const orderOptions = sort === 'rank' ? RANK_ORDER_OPTIONS : ORDER_OPTIONS;

  const activeFilterCount = useMemo(() => {
    let count = 0;
    if (filters.minSteamScore != null || filters.maxSteamScore != null)
      count += 1;
    if (filters.minSteamReviews != null || filters.maxSteamReviews != null)
      count += 1;
    if (filters.minPrice != null || filters.maxPrice != null) count += 1;
    if (filters.minFinalPrice != null || filters.maxFinalPrice != null)
      count += 1;
    if (filters.minDiscount != null) count += 1;
    if (filters.discountOnly) count += 1;
    if (filters.freeOnly) count += 1;
    return count;
  }, [filters]);

  const filterPanel = (
    <div className="game-discover-filters__panel">
      <div className="game-discover-filters__field">
        <span className="game-discover-filters__label">Steam 评分</span>
        <Space size={8}>
          <InputNumber
            min={0}
            max={100}
            placeholder="最低"
            value={filters.minSteamScore}
            onChange={(value) =>
              onFiltersChange({ ...filters, minSteamScore: value ?? undefined })
            }
          />
          <span>—</span>
          <InputNumber
            min={0}
            max={100}
            placeholder="最高"
            value={filters.maxSteamScore}
            onChange={(value) =>
              onFiltersChange({ ...filters, maxSteamScore: value ?? undefined })
            }
          />
        </Space>
      </div>
      <div className="game-discover-filters__field">
        <span className="game-discover-filters__label">评价人数</span>
        <Space size={8}>
          <InputNumber
            min={0}
            placeholder="最少"
            value={filters.minSteamReviews}
            onChange={(value) =>
              onFiltersChange({
                ...filters,
                minSteamReviews: value ?? undefined,
              })
            }
          />
          <span>—</span>
          <InputNumber
            min={0}
            placeholder="最多"
            value={filters.maxSteamReviews}
            onChange={(value) =>
              onFiltersChange({
                ...filters,
                maxSteamReviews: value ?? undefined,
              })
            }
          />
        </Space>
      </div>
      <div className="game-discover-filters__field">
        <span className="game-discover-filters__label">原价（元）</span>
        <Space size={8}>
          <InputNumber
            min={0}
            placeholder="最低"
            value={centToYuan(filters.minPrice)}
            onChange={(value) =>
              onFiltersChange({ ...filters, minPrice: yuanToCent(value) })
            }
          />
          <span>—</span>
          <InputNumber
            min={0}
            placeholder="最高"
            value={centToYuan(filters.maxPrice)}
            onChange={(value) =>
              onFiltersChange({ ...filters, maxPrice: yuanToCent(value) })
            }
          />
        </Space>
      </div>
      <div className="game-discover-filters__field">
        <span className="game-discover-filters__label">现价（元）</span>
        <Space size={8}>
          <InputNumber
            min={0}
            placeholder="最低"
            value={centToYuan(filters.minFinalPrice)}
            onChange={(value) =>
              onFiltersChange({ ...filters, minFinalPrice: yuanToCent(value) })
            }
          />
          <span>—</span>
          <InputNumber
            min={0}
            placeholder="最高"
            value={centToYuan(filters.maxFinalPrice)}
            onChange={(value) =>
              onFiltersChange({ ...filters, maxFinalPrice: yuanToCent(value) })
            }
          />
        </Space>
      </div>
      <div className="game-discover-filters__field">
        <span className="game-discover-filters__label">最低折扣</span>
        <Space.Compact>
          <InputNumber
            min={0}
            max={100}
            placeholder="如 30"
            value={filters.minDiscount}
            onChange={(value) =>
              onFiltersChange({ ...filters, minDiscount: value ?? undefined })
            }
          />
          <Space.Addon>%</Space.Addon>
        </Space.Compact>
      </div>
      <div className="game-discover-filters__checks">
        <Checkbox
          checked={Boolean(filters.discountOnly)}
          onChange={(event) =>
            onFiltersChange({
              ...filters,
              discountOnly: event.target.checked || undefined,
            })
          }
        >
          仅折扣
        </Checkbox>
        <Checkbox
          checked={Boolean(filters.freeOnly)}
          onChange={(event) =>
            onFiltersChange({
              ...filters,
              freeOnly: event.target.checked || undefined,
            })
          }
        >
          仅免费
        </Checkbox>
      </div>
      <Button
        type="link"
        size="small"
        className="game-discover-filters__reset"
        onClick={() => onFiltersChange({})}
      >
        清除筛选
      </Button>
    </div>
  );

  return (
    <div className="game-discover-filters">
      <Select
        className="game-discover-filters__sort"
        value={sort}
        options={sortOptions}
        onChange={onSortChange}
      />
      <Select
        className="game-discover-filters__order"
        value={order}
        options={orderOptions}
        onChange={onOrderChange}
      />
      <Popover
        trigger="click"
        placement="bottomLeft"
        content={filterPanel}
        classNames={{ root: 'game-discover-filters__popover' }}
      >
        <Button icon={<FilterOutlined />}>
          筛选{activeFilterCount > 0 ? ` (${activeFilterCount})` : ''}
        </Button>
      </Popover>
    </div>
  );
};

export default memo(GameDiscoverFilters);
