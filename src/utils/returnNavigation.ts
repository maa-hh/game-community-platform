export interface ReturnLocation {
  pathname: string;
  search: string;
  hash: string;
}

export interface ReturnNavigationState {
  returnTo: string;
}

/** React Router 为站内 history entry 写入递增 idx；大于 0 才能安全 POP。 */
export function canGoBackInApp() {
  const state = window.history.state as { idx?: unknown } | null;
  return typeof state?.idx === 'number' && state.idx > 0;
}

export function buildReturnNavigationState(
  location: ReturnLocation,
): ReturnNavigationState {
  const returnTo = `${location.pathname}${location.search}${location.hash}`;
  return {
    returnTo,
  };
}
