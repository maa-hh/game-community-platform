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

/**
 * 失效同一业务域下的所有派生缓存。
 *
 * 列表页通常会按分类、筛选条件和分页分别缓存。发生写操作后只删掉
 * 当前条件的 key 会让用户在切换筛选时看到旧快照，因此需要按业务前缀
 * 一次性清理整组缓存。
 */
export function invalidatePageDataCacheByPrefix(prefix: string): void {
  Array.from(pageDataCache.keys())
    .filter((key) => key.startsWith(prefix))
    .forEach((key) => invalidatePageDataCache(key));
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
