import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';

import { getUserSimpleByAccountIdApi } from '@/service/account';
import { getPageDataCache, setPageDataCache } from '@/hooks/pageDataCache';
import { useAppSelector } from '@/store';

export interface ProfileViewUser {
  accountId: number;
  username: string;
  avatar?: string;
  signature?: string;
}

export function useProfileView() {
  const [searchParams] = useSearchParams();
  const { user: me } = useAppSelector((state) => state.auth);
  const [viewUser, setViewUser] = useState<ProfileViewUser | null>(null);
  const [loading, setLoading] = useState(false);

  const accountIdParam = Number(searchParams.get('accountId') || '');
  const hasTarget = Boolean(accountIdParam && !Number.isNaN(accountIdParam));

  const isSelf = useMemo(() => {
    if (!hasTarget) return true;
    return Boolean(me?.accountId && accountIdParam === Number(me.accountId));
  }, [accountIdParam, hasTarget, me?.accountId]);

  useEffect(() => {
    if (!hasTarget || isSelf) {
      setViewUser(null);
      setLoading(false);
      return;
    }

    let cancelled = false;
    const cacheKey = `profile-user:${accountIdParam}`;
    const cachedUser = getPageDataCache<ProfileViewUser>(cacheKey);
    if (cachedUser) {
      setViewUser(cachedUser);
      setLoading(false);
      return undefined;
    }
    // accountId 变化时先清掉旧资料，避免请求期间短暂显示上一位用户的横幅。
    setViewUser(null);
    setLoading(true);

    const load = async () => {
      try {
        const res = await getUserSimpleByAccountIdApi(accountIdParam);
        if (!cancelled) {
          const nextUser = res.data
            ? {
                accountId: res.data.accountId,
                username: res.data.username,
                avatar: res.data.avatar,
                signature: res.data.signature,
              }
            : null;
          setViewUser(nextUser);
          if (nextUser) setPageDataCache(cacheKey, nextUser);
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    void load();
    return () => {
      cancelled = true;
    };
  }, [accountIdParam, hasTarget, isSelf]);

  return {
    isSelf,
    isOther: hasTarget && !isSelf,
    viewUser,
    loading,
  };
}
