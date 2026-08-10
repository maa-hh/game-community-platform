import hyRequest from './request';
import type { IDataType, IBanner, IGameItem } from './types';

// 获取首页 Banner 列表
export function getBanners() {
  return hyRequest.get<IDataType<IBanner[]>>({
    url: '/api/banner',
  });
}

// 获取首页游戏列表
export function getGameList() {
  return hyRequest.get<IDataType<IGameItem[]>>({
    url: '/api/games',
  });
}
