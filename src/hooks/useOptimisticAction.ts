import { useCallback, useRef, useState } from 'react';

export interface OptimisticActionHandlers<TResult> {
  /** 立即更新界面，通常写入 optimistic 状态或临时实体。 */
  apply: () => void;
  /** 发起真实请求。 */
  request: () => Promise<TResult>;
  /** 请求成功后，用服务端结果校正界面。 */
  commit: (result: TResult) => void;
  /** 请求失败后回滚 optimistic 状态。 */
  rollback: (error: unknown) => void;
  /** 无论成功失败都执行，例如维护外部 inflight 计数。 */
  finally?: () => void;
}

/**
 * 统一封装所有需要 optimistic update 的异步操作。
 *
 * key 是同一业务实体的互斥范围：同一个 key 在请求完成前不会重复执行，
 * 不同 key 可以并行，例如两个不同评论的点赞可以同时提交。
 */
export function useOptimisticAction() {
  const pendingRef = useRef(new Set<string>());
  const [pendingKeys, setPendingKeys] = useState<ReadonlySet<string>>(
    () => new Set(),
  );

  const isPending = useCallback(
    (key: string) => pendingKeys.has(key),
    [pendingKeys],
  );

  const run = useCallback(
    async <TResult>(
      key: string,
      handlers: OptimisticActionHandlers<TResult>,
    ): Promise<boolean> => {
      if (pendingRef.current.has(key)) return false;

      pendingRef.current.add(key);
      setPendingKeys((current) => {
        const next = new Set(current);
        next.add(key);
        return next;
      });

      try {
        handlers.apply();
        const result = await handlers.request();
        handlers.commit(result);
        return true;
      } catch (error) {
        handlers.rollback(error);
        return false;
      } finally {
        handlers.finally?.();
        pendingRef.current.delete(key);
        setPendingKeys((current) => {
          const next = new Set(current);
          next.delete(key);
          return next;
        });
      }
    },
    [],
  );

  return { run, isPending };
}
