import { useEffect, useState } from 'react';

const COSMETIC_UPDATED_EVENT = 'gc:cosmetic-updated';

/** 装备/卸下装扮后广播，各展示位重新拉取 decoration */
export function notifyCosmeticUpdated() {
  window.dispatchEvent(new CustomEvent(COSMETIC_UPDATED_EVENT));
}

/** 订阅装扮变更，用于触发 decoration 重新请求 */
export function useCosmeticRefreshToken() {
  const [token, setToken] = useState(0);

  useEffect(() => {
    const handler = () => setToken((value) => value + 1);
    window.addEventListener(COSMETIC_UPDATED_EVENT, handler);
    return () => window.removeEventListener(COSMETIC_UPDATED_EVENT, handler);
  }, []);

  return token;
}
