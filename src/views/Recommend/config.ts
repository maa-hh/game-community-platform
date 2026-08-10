import type { HotRankBoard } from '@/service/hotRank';

export const hotRankBoardTabs: Array<{ label: string; value: HotRankBoard }> = [
  { label: '总榜', value: 'total' },
  { label: '周榜', value: 'weekly' },
  { label: '日榜', value: 'daily' },
];

/** 分类下拉「全部」哨兵值（非真实分类 ID） */
export const ALL_CATEGORY_VALUE = 0;

export const hotRankEmptyText: Record<HotRankBoard, string> = {
  total: '暂无总榜数据',
  weekly: '暂无周榜数据',
  daily: '暂无日榜数据',
};
