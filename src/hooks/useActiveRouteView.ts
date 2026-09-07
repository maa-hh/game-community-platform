import { createContext, useContext } from 'react';

/** 当前路由实例是否正在 MainLayout 中展示。缓存页面也会继续挂载，但不可见实例应暂停副作用。 */
export const ActiveRouteViewContext = createContext(false);

export function useActiveRouteView(): boolean {
  return useContext(ActiveRouteViewContext);
}
