import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';
import type { ReactNode } from 'react';

import { useUserDecorations } from '@/hooks/useUserDecorations';
import type { IUserDecoration } from '@/types/cosmetic';

type DecorationGetter = (accountId?: number) => IUserDecoration | undefined;
type RegisterUser = (accountId: number) => () => void;

const DecorationGetterContext = createContext<DecorationGetter | null>(null);
const DecorationRegisterContext = createContext<RegisterUser | null>(null);

export function DecorationRegistryProvider({
  children,
}: {
  children: ReactNode;
}) {
  const [registry, setRegistry] = useState<Map<number, number>>(new Map());

  const register = useCallback<RegisterUser>((accountId) => {
    setRegistry((prev) => {
      const next = new Map(prev);
      next.set(accountId, (next.get(accountId) ?? 0) + 1);
      return next;
    });
    return () => {
      setRegistry((prev) => {
        const next = new Map(prev);
        const count = (next.get(accountId) ?? 1) - 1;
        if (count <= 0) next.delete(accountId);
        else next.set(accountId, count);
        return next;
      });
    };
  }, []);

  const accountIds = useMemo(
    () => Array.from(registry.keys()).sort((a, b) => a - b),
    [registry],
  );
  const { get } = useUserDecorations(accountIds);

  const getter = useCallback<DecorationGetter>(
    (accountId) => (accountId ? get(Number(accountId)) : undefined),
    [get],
  );

  return (
    <DecorationRegisterContext.Provider value={register}>
      <DecorationGetterContext.Provider value={getter}>
        {children}
      </DecorationGetterContext.Provider>
    </DecorationRegisterContext.Provider>
  );
}

export function useRegisterDecorationUser(accountId?: number) {
  const register = useContext(DecorationRegisterContext);

  useEffect(() => {
    if (!register || !accountId || accountId <= 0) return;
    return register(accountId);
  }, [register, accountId]);
}

export function useDecorationRegistry(accountId?: number) {
  const enabled = Boolean(accountId && accountId > 0);
  useRegisterDecorationUser(enabled ? accountId : undefined);
  const get = useContext(DecorationGetterContext);
  return enabled ? get?.(accountId) : undefined;
}
