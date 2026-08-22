import type { IGameDetail } from '@/types/game';

export interface IGameCoverOption {
  appId: number;
  gameName: string;
  url: string;
  label: string;
}

/** 从游戏详情收集可选封面（头图 + 截图 + 预告片缩略图） */
export function collectGameCoverOptions(
  detail: IGameDetail,
): IGameCoverOption[] {
  const options: IGameCoverOption[] = [];
  const seen = new Set<string>();

  const push = (url: string | undefined, label: string) => {
    const key = url?.trim();
    if (!key || seen.has(key)) return;
    seen.add(key);
    options.push({
      appId: detail.appId,
      gameName: detail.name,
      url: key,
      label,
    });
  };

  // 这里只展示详情接口实际返回的封面。
  // steamCoverCandidates() 还包含加载失败时的 CDN 兜底地址，不能把这些
  // 推测地址当成候选项展示，否则新格式的 Steam 资源会出现 404 空白卡片。
  push(detail.coverUrl, '头图');

  detail.screenshots?.forEach((shot, index) => {
    push(shot.fullUrl || shot.thumbnailUrl, `截图 ${index + 1}`);
  });

  detail.movies?.forEach((movie, index) => {
    push(movie.thumbnailUrl, `预告 ${index + 1}`);
  });

  return options;
}
