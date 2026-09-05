const pageDataCache = new Map<string, unknown>();
const pageDataCacheListeners = new Map<string, Set<() => void>>();

export function getPageDataCache<T>(key: string): T | undefined {
  return pageDataCache.get(key) as T | undefined;
}

export function setPageDataCache<T>(key: string, value: T): void {
  pageDataCache.set(key, value);
}

/** 清空缓存但不广播；用于路由/账号切换时即将卸载的页面。 */
export function clearPageDataCache(): void {
  pageDataCache.clear();
}

export function invalidatePageDataCache(key?: string): void {
  if (key) {
    pageDataCache.delete(key);
    pageDataCacheListeners.get(key)?.forEach((listener) => listener());
    return;
  }
  clearPageDataCache();
  pageDataCacheListeners.forEach((listeners) => {
    listeners.forEach((listener) => listener());
  });
}

export function subscribePageDataCache(
  key: string,
  listener: () => void,
): () => void {
  const listeners = pageDataCacheListeners.get(key) ?? new Set();
  listeners.add(listener);
  pageDataCacheListeners.set(key, listeners);
  return () => {
    listeners.delete(listener);
    if (listeners.size === 0) pageDataCacheListeners.delete(key);
  };
}
