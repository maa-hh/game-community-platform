import type { IGameDetail } from '@/types/game';
import { steamCoverCandidates } from '@/utils/steamImage';

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

  steamCoverCandidates(detail.appId, detail.coverUrl).forEach((url, index) => {
    push(url, index === 0 ? '头图' : `头图备选 ${index}`);
  });

  detail.screenshots?.forEach((shot, index) => {
    push(shot.fullUrl || shot.thumbnailUrl, `截图 ${index + 1}`);
  });

  detail.movies?.forEach((movie, index) => {
    push(movie.thumbnailUrl, `预告 ${index + 1}`);
  });

  return options;
}
