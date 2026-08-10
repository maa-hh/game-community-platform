/** Steam 商店头图（高清封面），用于替代库同步的小 icon */
export function steamHeaderImageUrl(appId: number): string {
  return `https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/${appId}/header.jpg`;
}

/** Steam 商店高分辨率胶囊图，老游戏没有时由后续头图地址兜底。 */
export function steamHighResolutionCapsuleUrl(appId: number): string {
  return `https://cdn.akamai.steamstatic.com/steam/apps/${appId}/capsule_616x353.jpg`;
}

/** 判断 Steam 地址是否为不适合游戏卡片展示的小尺寸资源。 */
export function isLowResolutionSteamCover(url?: string | null): boolean {
  const lower = url?.trim().toLowerCase();
  if (!lower) return false;
  return (
    lower.includes('capsule_sm_120') ||
    lower.includes('capsule_231x87') ||
    lower.includes('capsule_184x69') ||
    lower.includes('small_capsule') ||
    lower.includes('/logo') ||
    lower.includes('/icon')
  );
}

/** 按优先级返回封面候选 URL（加载失败时依次尝试） */
export function steamCoverCandidates(
  appId: number,
  coverUrl?: string | null,
  iconUrl?: string | null,
): string[] {
  const result: string[] = [];
  const push = (url?: string | null) => {
    const value = url?.trim();
    if (value && !result.includes(value)) {
      result.push(value);
    }
  };

  const cover = coverUrl?.trim();
  // 数据库中的 hashed store_item_assets 地址通常是唯一可靠的地址，
  // 先保留高清版本；低清地址放到末尾作为最终兜底，不能直接丢弃。
  if (cover && !isLowResolutionSteamCover(cover)) {
    push(cover);
  }

  if (Number.isFinite(appId) && appId > 0) {
    push(steamHighResolutionCapsuleUrl(appId));
    push(steamHeaderImageUrl(appId));
    push(
      `https://cdn.cloudflare.steamstatic.com/steam/apps/${appId}/header.jpg`,
    );
    push(
      `https://cdn.cloudflare.steamstatic.com/steam/apps/${appId}/capsule_231x87.jpg`,
    );
    push(
      `https://cdn.cloudflare.steamstatic.com/steam/apps/${appId}/library_600x900.jpg`,
    );
  }

  push(iconUrl);
  if (cover) {
    push(cover);
  }

  return result;
}

/** 返回 Steam 成就图标候选地址，兼容旧版 CDN 域名。 */
export function steamAchievementImageCandidates(
  iconUrl?: string | null,
): string[] {
  const result: string[] = [];
  const push = (url?: string | null) => {
    const value = url?.trim();
    if (value && !result.includes(value)) {
      result.push(value);
    }
  };

  const original = iconUrl?.trim();
  if (original) {
    const normalized = original
      .replace('steamcdn-a.akamaihd.net', 'media.steampowered.com')
      .replace('cdn.akamai.steamstatic.com', 'media.steampowered.com');
    push(normalized);
    push(
      normalized.replace(
        'media.steampowered.com',
        'cdn.akamai.steamstatic.com',
      ),
    );
    push(
      normalized.replace(
        'media.steampowered.com',
        'cdn.cloudflare.steamstatic.com',
      ),
    );
    // 旧地址最后尝试，避免旧 CDN 的慢连接阻塞页面首屏。
    push(original);
  }
  return result;
}

/** 列表/卡片封面：优先百科头图，否则用 Steam 标准头图，最后才用小图标 */
export function resolveGameCoverUrl(
  appId: number,
  coverUrl?: string | null,
  iconUrl?: string | null,
): string | undefined {
  return steamCoverCandidates(appId, coverUrl, iconUrl)[0];
}
