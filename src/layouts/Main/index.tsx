import React, {
  useCallback,
  useContext,
  useEffect,
  useRef,
  useState,
} from 'react';
import {
  UNSAFE_LocationContext as LocationContext,
  UNSAFE_RouteContext as RouteContext,
  useLocation,
  useMatches,
  useOutlet,
} from 'react-router-dom';

import { useAppSelector } from '@/store';
import PageTools from '@/components/PageTools';
import { PAGE_REFRESH_EVENT } from '@/utils/pageRefresh';

import './style.less';

const MAX_CACHED_VIEWS = 8;

interface RouteCacheHandle {
  disableKeepAlive?: boolean;
}

interface CachedView {
  cacheKey: string;
  element: React.ReactElement | null;
  locationContext: React.ContextType<typeof LocationContext>;
  routeContext: React.ContextType<typeof RouteContext>;
  lastAccessed: number;
  renderVersion: number;
}

/** 站内页布局：统一内容限宽盒（顶栏在 RootLayout） */
function MainLayout() {
  const location = useLocation();
  const matches = useMatches();
  const outlet = useOutlet();
  const locationContext = useContext(LocationContext);
  const routeContext = useContext(RouteContext);
  const accountScope = useAppSelector((state) =>
    String(state.auth.user?.accountId ?? 'anonymous'),
  );
  const cachedViewsRef = useRef(new Map<string, CachedView>());
  const accessSequenceRef = useRef(0);
  const cacheAccountScopeRef = useRef(accountScope);
  const refreshTimerRef = useRef<number | undefined>(undefined);
  const [refreshing, setRefreshing] = useState(false);
  const [, setRenderRevision] = useState(0);
  const [standaloneRenderVersion, setStandaloneRenderVersion] = useState(0);

  useEffect(
    () => () => {
      if (refreshTimerRef.current != null) {
        window.clearTimeout(refreshTimerRef.current);
      }
    },
    [],
  );

  // 登录态变化后不能继续复用上一个账号的列表实例；统一清空布局缓存，
  // 避免因为缓存键变化把当前页面当成新页面挂载，导致返回位置丢失。
  if (cacheAccountScopeRef.current !== accountScope) {
    cachedViewsRef.current.clear();
    accessSequenceRef.current = 0;
    cacheAccountScopeRef.current = accountScope;
  }

  const routeCacheKey = location.pathname;
  // 普通页面默认缓存；只有详情、编辑等明确标记的重页面不缓存。
  // 后续新增普通页面无需再维护白名单。
  const keepAlive = !matches.some(
    (match) =>
      (match.handle as RouteCacheHandle | undefined)?.disableKeepAlive === true,
  );

  if (keepAlive) {
    const cachedView = cachedViewsRef.current.get(routeCacheKey);
    if (cachedView) {
      // query/hash 变化复用页面实例，只刷新路由上下文。
      cachedView.locationContext = locationContext;
      cachedView.routeContext = routeContext;
      cachedView.lastAccessed = ++accessSequenceRef.current;
    } else {
      cachedViewsRef.current.set(routeCacheKey, {
        cacheKey: routeCacheKey,
        element: outlet,
        locationContext,
        routeContext,
        lastAccessed: ++accessSequenceRef.current,
        renderVersion: 0,
      });
    }
    while (cachedViewsRef.current.size > MAX_CACHED_VIEWS) {
      let oldestView: CachedView | undefined;
      cachedViewsRef.current.forEach((view) => {
        if (!oldestView || view.lastAccessed < oldestView.lastAccessed) {
          oldestView = view;
        }
      });
      if (!oldestView) break;
      cachedViewsRef.current.delete(oldestView.cacheKey);
    }
  }

  const cachedViews = Array.from(cachedViewsRef.current.values());

  const refreshCurrentPage = useCallback(() => {
    if (refreshing) return;
    setRefreshing(true);

    const refreshEvent = new Event(PAGE_REFRESH_EVENT, { cancelable: true });
    const handledByPage = !window.dispatchEvent(refreshEvent);

    if (!handledByPage) {
      const currentView = cachedViewsRef.current.get(routeCacheKey);
      if (keepAlive && currentView) {
        currentView.renderVersion += 1;
        setRenderRevision((value) => value + 1);
      } else {
        setStandaloneRenderVersion((value) => value + 1);
      }
    }

    if (refreshTimerRef.current != null) {
      window.clearTimeout(refreshTimerRef.current);
    }
    refreshTimerRef.current = window.setTimeout(() => {
      setRefreshing(false);
    }, 400);
  }, [keepAlive, refreshing, routeCacheKey]);

  return (
    <div className="main-layout">
      <main className="main-layout__content">
        <div className="main-layout__container">
          {cachedViews.map((view) => {
            const active = keepAlive && view.cacheKey === routeCacheKey;
            return (
              <div
                key={`${view.cacheKey}:${view.renderVersion}`}
                className={`main-layout__route-view${
                  active ? ' main-layout__route-view--active' : ''
                }`}
                aria-hidden={!active}
                hidden={!active}
              >
                <LocationContext.Provider value={view.locationContext}>
                  <RouteContext.Provider value={view.routeContext}>
                    {view.element}
                  </RouteContext.Provider>
                </LocationContext.Provider>
              </div>
            );
          })}
          {!keepAlive ? (
            <div
              key={`${routeCacheKey}:${standaloneRenderVersion}`}
              className="main-layout__route-view main-layout__route-view--active"
            >
              {outlet}
            </div>
          ) : null}
        </div>
      </main>
      <PageTools refreshing={refreshing} onRefresh={refreshCurrentPage} />
    </div>
  );
}

export default MainLayout;
