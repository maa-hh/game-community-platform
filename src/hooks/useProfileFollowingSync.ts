import { useCallback } from 'react';

import { useAppDispatch, useAppSelector } from '@/store';
import { markProfileDataDirty } from '@/store/modules/profileRealtime';
import { PROFILE_DATA_DOMAIN } from '@/types/profileRealtime';
import { invalidateProfileDataCaches } from '@/utils/profileDataCache';

/** 关注关系变化后，让个人主页的统计和关注列表立即放弃旧快照。 */
export function useProfileFollowingSync() {
  const dispatch = useAppDispatch();
  const accountId = useAppSelector((state) => state.auth.user?.accountId);

  return useCallback(() => {
    if (!accountId) return;
    const domains = [
      PROFILE_DATA_DOMAIN.STATS,
      PROFILE_DATA_DOMAIN.FOLLOWING,
      PROFILE_DATA_DOMAIN.FEED,
    ] as const;
    dispatch(
      markProfileDataDirty({
        accountId,
        domains: [...domains],
      }),
    );
    invalidateProfileDataCaches(accountId, domains);
  }, [accountId, dispatch]);
}
