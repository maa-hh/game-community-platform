import React, { useCallback, useContext, useRef, useState } from 'react';
import {
  UNSAFE_LocationContext as LocationContext,
  UNSAFE_RouteContext as RouteContext,
  useLocation,
  useMatches,
  useOutlet,
} from 'react-router-dom';

import { useAppSelector } from '@/store';
import PageTools from '@/components/PageTools';
import { clearPageDataCache } from '@/hooks/pageDataCache';
import { ActiveRouteViewContext } from '@/hooks/useActiveRouteView';
import { isPrimaryNavigationState } from '@/utils/primaryNavigation';
import {
  PAGE_REFRESH_EVENT,
  type PageRefreshEventDetail,
} from '@/utils/pageRefresh';

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
  const primaryNavigationEntryRef = useRef<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const [, setRenderRevision] = useState(0);
  const [standaloneRenderVersion, setStandaloneRenderVersion] = useState(0);

  // 登录态变化后不能继续复用上一个账号的列表实例。
  if (cacheAccountScopeRef.current !== accountScope) {
    cachedViewsRef.current.clear();
    clearPageDataCache();
    accessSequenceRef.current = 0;
    cacheAccountScopeRef.current = accountScope;
  }

  // 一级导航只重置页面实例（筛选、局部展开等 UI 状态），不清空数据缓存。
  // 因此重新进入页面会呈现初始 UI，但命中缓存的数据不会重复请求。
  if (
    isPrimaryNavigationState(location.state) &&
    primaryNavigationEntryRef.current !== location.key
  ) {
    cachedViewsRef.current.clear();
    accessSequenceRef.current = 0;
    primaryNavigationEntryRef.current = location.key;
  }

  // 用户作用域属于页面实例身份的一部分：登录/退出后即使路径没变，也必须
  // 销毁旧实例，不能让新账号短暂看到前一账号的本地状态。
  // 个人主页的 tab/search 可以复用实例，但不同 accountId 是不同资料页；
  // 否则切换或返回到另一位用户时会先绘制上一位用户的资料横幅。
  const profileAccountId =
    location.pathname === '/profile'
      ? new URLSearchParams(location.search).get('accountId')
      : null;
  const routeCacheKey = `${accountScope}:${location.pathname}${
    profileAccountId ? `?accountId=${profileAccountId}` : ''
  }`;
  // 普通页面在下钻到详情、编辑页时保留实例；顶栏一级导航会在点击时
  // 同步清空本缓存，因此切换 tab 时一定重新以初始状态挂载，而不是复用旧页。
  const keepAlive = !matches.some(
    (match) =>
      (match.handle as RouteCacheHandle | undefined)?.disableKeepAlive === true,
  );
  if (keepAlive) {
    const cachedView = cachedViewsRef.current.get(routeCacheKey);
    if (cachedView) {
      // 页面内部筛选使用 query/hash，复用当前实例并只更新路由上下文。
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
    window.scrollTo({ top: 0, left: 0, behavior: 'auto' });

    const tasks: Promise<unknown>[] = [];
    const detail: PageRefreshEventDetail = {
      track: (task) => tasks.push(task),
    };
    const refreshEvent = new CustomEvent(PAGE_REFRESH_EVENT, {
      cancelable: true,
      detail,
    });
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

    void Promise.allSettled(tasks).finally(() => {
      setRefreshing(false);
    });
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
              >
                <LocationContext.Provider value={view.locationContext}>
                  <RouteContext.Provider value={view.routeContext}>
                    <ActiveRouteViewContext.Provider value={active}>
                      {view.element}
                    </ActiveRouteViewContext.Provider>
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
              <ActiveRouteViewContext.Provider value>
                {outlet}
              </ActiveRouteViewContext.Provider>
            </div>
          ) : null}
        </div>
      </main>
      <PageTools refreshing={refreshing} onRefresh={refreshCurrentPage} />
    </div>
  );
}

export default MainLayout;
