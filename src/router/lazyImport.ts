const CHUNK_RELOAD_KEY_PREFIX = 'chunk-reload:';

function isChunkLoadError(error: unknown): boolean {
  const message =
    error instanceof Error ? `${error.name}: ${error.message}` : String(error);
  return /ChunkLoadError|Loading chunk \d+ failed|Failed to fetch dynamically imported module/i.test(
    message,
  );
}

function withSessionStorage(action: (storage: Storage) => void): void {
  try {
    action(window.sessionStorage);
  } catch {
    // 隐私模式可能禁用 sessionStorage；此时直接交给路由错误页处理。
  }
}

/**
 * 处理发布后旧 HTML 引用已删除 chunk 的场景：同一路由资源只自动刷新一次，
 * 第二次仍失败则抛给路由错误页，避免无限刷新。
 */
export async function importWithChunkReload<T>(
  key: string,
  importer: () => Promise<T>,
): Promise<T> {
  const reloadKey = `${CHUNK_RELOAD_KEY_PREFIX}${key}`;
  try {
    const module = await importer();
    withSessionStorage((storage) => storage.removeItem(reloadKey));
    return module;
  } catch (error) {
    if (!isChunkLoadError(error) || typeof window === 'undefined') throw error;

    let shouldReload = false;
    withSessionStorage((storage) => {
      if (!storage.getItem(reloadKey)) {
        storage.setItem(reloadKey, '1');
        shouldReload = true;
      }
    });
    if (!shouldReload) throw error;

    window.location.reload();
    // 页面即将卸载，保持 Promise pending，避免刷新前短暂渲染错误页。
    return new Promise<T>(() => undefined);
  }
}
