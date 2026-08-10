import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';

import { getUserSimpleByAccountIdApi } from '@/service/account';
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
    setLoading(true);

    const load = async () => {
      try {
        const res = await getUserSimpleByAccountIdApi(accountIdParam);
        if (!cancelled) {
          setViewUser(
            res.data
              ? {
                  accountId: res.data.accountId,
                  username: res.data.username,
                  avatar: res.data.avatar,
                  signature: res.data.signature,
                }
              : null,
          );
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
