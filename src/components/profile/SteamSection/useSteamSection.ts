import { useCallback, useEffect, useRef, useState } from 'react';
import { Modal, message } from 'antd';

import {
  fetchSteamAuthUrlApi,
  fetchSteamLibraryApi,
  fetchSteamProfileApi,
  fetchUserSteamLibraryByAccountApi,
  fetchUserSteamProfileByAccountApi,
  syncSteamLibraryApi,
  unbindSteamApi,
} from '@/service/steam';
import type { ISteamGameItem, ISteamProfile } from '@/types/game';
import { formatApiError } from '@/utils/apiError';
import { getPageDataCache, setPageDataCache } from '@/hooks/pageDataCache';
import { usePageRefresh } from '@/hooks/usePageRefresh';

import type { ISteamSectionProps } from './types';

const LIBRARY_SYNC_STALE_MS = 24 * 60 * 60 * 1000;

type LibrarySyncCursor = {
  page: number;
  syncId?: string;
};

interface SteamSectionCache {
  profile: ISteamProfile | null;
  library: ISteamGameItem[];
  libraryPrivate: boolean;
}

/** 没有同步时间或同步时间超过 24 小时，就在进入个人资料时懒同步。 */
function isLibraryStale(librarySyncedAt?: string) {
  if (!librarySyncedAt) return true;
  const syncedTime = Date.parse(librarySyncedAt);
  return (
    !Number.isFinite(syncedTime) ||
    Date.now() - syncedTime >= LIBRARY_SYNC_STALE_MS
  );
}

export function useSteamSection({
  targetAccountId,
  readOnly = false,
}: Pick<ISteamSectionProps, 'targetAccountId' | 'readOnly'> = {}) {
  const isOtherView = readOnly && targetAccountId != null;
  const cacheKey = `steam-section:${targetAccountId ?? 'self'}`;
  const cached = getPageDataCache<SteamSectionCache>(cacheKey);

  const [loading, setLoading] = useState(!cached);
  const [binding, setBinding] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [syncProgress, setSyncProgress] = useState<{
    processed: number;
    total: number;
  } | null>(null);
  const [profile, setProfile] = useState<ISteamProfile | null>(
    cached?.profile ?? null,
  );
  const [library, setLibrary] = useState<ISteamGameItem[]>(
    cached?.library ?? [],
  );
  const [libraryPrivate, setLibraryPrivate] = useState(
    cached?.libraryPrivate ?? false,
  );
  const loadedRef = useRef(Boolean(cached));
  const syncingRef = useRef(false);
  const pendingSyncRef = useRef<LibrarySyncCursor | null>(null);

  const load = useCallback(async () => {
    if (!loadedRef.current) setLoading(true);
    setLibraryPrivate(false);
    try {
      const profileRes = isOtherView
        ? await fetchUserSteamProfileByAccountApi(targetAccountId)
        : await fetchSteamProfileApi();
      const steamProfile = profileRes.data;
      loadedRef.current = true;
      setProfile(steamProfile);
      if (!steamProfile?.steamId) {
        setLibrary([]);
        setPageDataCache(cacheKey, {
          profile: steamProfile,
          library: [],
          libraryPrivate: false,
        });
        return;
      }
      if (isOtherView && steamProfile.libraryPublic === false) {
        setLibrary([]);
        setLibraryPrivate(true);
        setPageDataCache(cacheKey, {
          profile: steamProfile,
          library: [],
          libraryPrivate: true,
        });
        return;
      }
      try {
        const libraryRes = isOtherView
          ? await fetchUserSteamLibraryByAccountApi(targetAccountId)
          : await fetchSteamLibraryApi();
        const nextLibrary = libraryRes.data || [];
        setLibrary(nextLibrary);
        setPageDataCache(cacheKey, {
          profile: steamProfile,
          library: nextLibrary,
          libraryPrivate: false,
        });
      } catch (libraryErr) {
        setLibrary([]);
        if (isOtherView) {
          const msg = formatApiError('', libraryErr);
          if (msg.includes('未公开')) {
            setLibraryPrivate(true);
            return;
          }
        }
        message.warning(
          formatApiError('游戏库加载失败，可点击同步重试', libraryErr),
        );
      }
    } catch (err) {
      if (!isOtherView) {
        message.error(formatApiError('加载 Steam 信息失败', err));
      }
    } finally {
      setLoading(false);
    }
  }, [cacheKey, isOtherView, targetAccountId]);

  useEffect(() => {
    if (cached) return;
    void load();
  }, [cached, load]);

  usePageRefresh(() => load(), true);

  const bindSteam = useCallback(() => {
    Modal.confirm({
      title: '确认绑定 Steam？',
      content:
        '请确认你已有 Steam 账号，并且当前环境可以正常登录 Steam。确认后将跳转至 Steam 授权页面。',
      okText: '确认并继续',
      cancelText: '取消',
      onOk: async () => {
        setBinding(true);
        try {
          const res = await fetchSteamAuthUrlApi();
          const url = res.data?.url;
          if (!url) {
            message.error('获取授权地址失败');
            return;
          }
          window.location.assign(url);
        } catch (err) {
          message.error(formatApiError('绑定 Steam 失败', err));
        } finally {
          setBinding(false);
        }
      },
    });
  }, []);

  const syncLibrary = useCallback(async () => {
    if (syncingRef.current) return;
    syncingRef.current = true;
    setSyncing(true);
    setSyncProgress({ processed: 0, total: 0 });
    try {
      let page = pendingSyncRef.current?.page ?? 0;
      let syncId = pendingSyncRef.current?.syncId;
      let completed = false;
      let libraryPublic = true;

      while (!completed) {
        pendingSyncRef.current = { page, syncId };
        const response = await syncSteamLibraryApi({ page, syncId });
        const result = response.data;
        libraryPublic = result.libraryPublic;
        setSyncProgress({
          processed: result.processedCount,
          total: result.total,
        });
        completed = result.completed;
        syncId = result.syncId;
        page = result.nextPage;
        pendingSyncRef.current = { page, syncId };
      }

      if (!libraryPublic) {
        pendingSyncRef.current = null;
        message.info('Steam 游戏库未公开，无法同步');
        return;
      }
      pendingSyncRef.current = null;
      message.success('游戏库同步成功');
      await load();
    } catch (err) {
      message.error(formatApiError('同步失败', err));
    } finally {
      syncingRef.current = false;
      setSyncing(false);
    }
  }, [load]);

  useEffect(() => {
    if (isOtherView || syncingRef.current || !profile?.steamId) {
      return;
    }
    if (!isLibraryStale(profile.librarySyncedAt)) {
      return;
    }
    void syncLibrary();
  }, [isOtherView, profile, syncLibrary]);

  useEffect(() => {
    if (isOtherView) return undefined;
    const handleBindSuccess = () => {
      // 绑定成功只刷新资料，懒同步 effect 统一负责启动新版本同步。
      void load();
    };
    window.addEventListener('steam-bind-success', handleBindSuccess);
    return () => {
      window.removeEventListener('steam-bind-success', handleBindSuccess);
    };
  }, [isOtherView, load, syncLibrary]);

  const unbind = useCallback(() => {
    Modal.confirm({
      title: '确认解绑 Steam？',
      content: '解绑后将无法展示 Steam 游戏库，可随时重新绑定。',
      okText: '解绑',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        try {
          await unbindSteamApi();
          message.success('已解绑 Steam');
          setProfile(null);
          setLibrary([]);
          setPageDataCache(cacheKey, {
            profile: null,
            library: [],
            libraryPrivate: false,
          });
          window.dispatchEvent(new CustomEvent('steam-profile-changed'));
        } catch (err) {
          message.error(formatApiError('解绑失败', err));
          throw err;
        }
      },
    });
  }, [cacheKey]);

  const bound = Boolean(profile?.steamId || profile?.bound);

  return {
    loading,
    binding,
    syncing,
    syncProgress,
    profile,
    library,
    libraryPrivate,
    bound,
    readOnly,
    bindSteam,
    syncLibrary,
    unbind,
    reload: load,
  };
}
