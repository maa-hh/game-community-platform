import { useEffect, useRef } from 'react';
import { useLocation } from 'react-router-dom';

import { PAGE_REFRESH_EVENT, type PageRefreshEvent } from '@/utils/pageRefresh';

type RefreshHandler = () => void | Promise<void>;

/**
 * 让没有 FeedPanel 的页面也接入统一的页面刷新事件。
 * MainLayout 会保留多个页面实例，因此只响应当前完整 URL 的实例。
 */
export function usePageRefresh(handler: RefreshHandler, enabled = true): void {
  const location = useLocation();
  const handlerRef = useRef(handler);
  const pageIdentity = `${location.pathname}${location.search}${location.hash}`;

  useEffect(() => {
    handlerRef.current = handler;
  }, [handler]);

  useEffect(() => {
    if (!enabled) return undefined;

    const handleRefresh = (event: Event) => {
      const currentIdentity = `${window.location.pathname}${window.location.search}${window.location.hash}`;
      if (currentIdentity !== pageIdentity) return;
      event.preventDefault();
      const task = Promise.resolve()
        .then(() => handlerRef.current())
        .catch(() => undefined);
      (event as PageRefreshEvent).detail?.track(task);
    };

    window.addEventListener(PAGE_REFRESH_EVENT, handleRefresh);
    return () => window.removeEventListener(PAGE_REFRESH_EVENT, handleRefresh);
  }, [enabled, pageIdentity]);
}
