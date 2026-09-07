import {
  invalidatePageDataCache,
  invalidatePageDataCacheByPrefix,
} from '@/hooks/pageDataCache';
import {
  PROFILE_DATA_DOMAIN,
  type ProfileDataDomain,
} from '@/types/profileRealtime';

export function invalidateProfileDataCaches(
  accountId: number | undefined,
  domains: readonly ProfileDataDomain[],
): void {
  if (accountId == null) return;

  const selfFeedPrefix = `profile-feed:self:${accountId}:`;
  domains.forEach((domain) => {
    switch (domain) {
      case PROFILE_DATA_DOMAIN.BASE:
        invalidatePageDataCache(`profile-user:${accountId}`);
        break;
      case PROFILE_DATA_DOMAIN.STATS:
        invalidatePageDataCache(`profile-stats:${accountId}`);
        break;
      case PROFILE_DATA_DOMAIN.FOLLOWING:
        invalidatePageDataCache('profile-users:following');
        break;
      case PROFILE_DATA_DOMAIN.FOLLOWERS:
        invalidatePageDataCache('profile-users:followers');
        break;
      case PROFILE_DATA_DOMAIN.POSTS:
        invalidatePageDataCacheByPrefix(`${selfFeedPrefix}posts:`);
        break;
      case PROFILE_DATA_DOMAIN.HISTORY:
      case PROFILE_DATA_DOMAIN.LIKED:
      case PROFILE_DATA_DOMAIN.RECEIVED:
      case PROFILE_DATA_DOMAIN.FAVORITES:
      case PROFILE_DATA_DOMAIN.COMMENTS:
        invalidatePageDataCacheByPrefix(
          `${selfFeedPrefix}${domain.replace('profile.', '')}:`,
        );
        break;
      case PROFILE_DATA_DOMAIN.FEED:
        invalidatePageDataCacheByPrefix(`feed:${accountId}:`);
        break;
      default:
        break;
    }
  });
}

export function invalidateOwnProfilePostCache(
  accountId: number | undefined,
  tab: 'published' | 'draft',
): void {
  if (accountId == null) return;
  invalidatePageDataCache(`profile-feed:self:${accountId}:posts:${tab}`);
}

export function invalidateOwnProfilePostCaches(
  accountId: number | undefined,
  tabs: readonly ('published' | 'draft')[],
): void {
  Array.from(new Set(tabs)).forEach((tab) => {
    invalidateOwnProfilePostCache(accountId, tab);
  });
}
