export interface PrimaryNavigationState {
  resetPrimaryNavigationState?: true;
}

export const primaryNavigationState: PrimaryNavigationState = {
  resetPrimaryNavigationState: true,
};

export function isPrimaryNavigationState(
  state: unknown,
): state is PrimaryNavigationState {
  return (
    typeof state === 'object' &&
    state !== null &&
    (state as PrimaryNavigationState).resetPrimaryNavigationState === true
  );
}
