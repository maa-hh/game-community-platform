import hyRequest from './request';
import type { IDataType, IRecommendData } from './types';

// 获取推荐页数据（banner + 热门游戏）
export function getRecommendData() {
  return hyRequest.get<IDataType<IRecommendData>>({
    url: '/api/recommend',
  });
}
