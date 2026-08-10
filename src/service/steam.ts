import hyRequest from './request';
import type { IDataType } from './types';
import type {
  ISteamGameItem,
  ISteamGameStats,
  ISteamLibrarySync,
  ISteamProfile,
} from '@/types/game';

export interface ISteamAuthUrlResult {
  url: string;
}

/** 获取 Steam OpenID 授权跳转地址 */
export function fetchSteamAuthUrlApi() {
  return hyRequest.get<IDataType<ISteamAuthUrlResult>>({
    url: '/steam/auth-url',
  });
}

/** 当前用户 Steam 资料（未绑定时 data 可能为 null） */
export function fetchSteamProfileApi() {
  return hyRequest.get<IDataType<ISteamProfile | null>>({
    url: '/steam/profile',
  });
}

/** 当前用户 Steam 游戏库 */
export function fetchSteamLibraryApi() {
  return hyRequest.get<IDataType<ISteamGameItem[]>>({
    url: '/steam/library',
  });
}

/** 当前用户对指定 Steam 游戏的游玩/成就统计 */
export function fetchSteamGameStatsApi(appId: number) {
  return hyRequest.get<IDataType<ISteamGameStats>>({
    url: `/steam/games/${appId}/stats`,
  });
}

/** 手动提交当前用户的 Steam 成就同步任务。 */
export function syncSteamGameAchievementsApi(appId: number) {
  return hyRequest.post<IDataType<ISteamGameStats>>({
    url: `/steam/games/${appId}/achievements/sync`,
  });
}

/** 分页增量同步 Steam 游戏库，每次由后端处理 20 个游戏。 */
export function syncSteamLibraryApi(query: { page: number; syncId?: string }) {
  return hyRequest.post<IDataType<ISteamLibrarySync>>({
    url: '/steam/sync',
    params: query,
  });
}

/** 解绑 Steam */
export function unbindSteamApi() {
  return hyRequest.delete<IDataType<null>>({
    url: '/steam/unbind',
  });
}

/** 指定用户的 Steam 资料（他人主页，accountId 对外） */
export function fetchUserSteamProfileByAccountApi(accountId: number) {
  return hyRequest.get<IDataType<ISteamProfile | null>>({
    url: `/steam/users/by-account/${accountId}/profile`,
  });
}

/** 指定用户的 Steam 游戏库（他人主页，需游戏库公开） */
export function fetchUserSteamLibraryByAccountApi(accountId: number) {
  return hyRequest.get<IDataType<ISteamGameItem[]>>({
    url: `/steam/users/by-account/${accountId}/library`,
  });
}
