import { createSlice } from '@reduxjs/toolkit';

import type { ProfileDataDomain } from '@/types/profileRealtime';

interface ProfileRealtimeState {
  dirtyByAccount: Record<string, ProfileDataDomain[]>;
  followFeedReloadRequired: boolean;
}

const initialState: ProfileRealtimeState = {
  dirtyByAccount: {},
  followFeedReloadRequired: false,
};

const profileRealtimeSlice = createSlice({
  name: 'profileRealtime',
  initialState,
  reducers: {
    markProfileDataDirty(
      state,
      action: {
        payload: { accountId: number; domains: ProfileDataDomain[] };
      },
    ) {
      const key = String(action.payload.accountId);
      const current = state.dirtyByAccount[key] ?? [];
      state.dirtyByAccount[key] = Array.from(
        new Set([...current, ...action.payload.domains]),
      );
    },
    clearProfileDataDirty(
      state,
      action: {
        payload: { accountId: number; domains: ProfileDataDomain[] };
      },
    ) {
      const key = String(action.payload.accountId);
      const remaining = (state.dirtyByAccount[key] ?? []).filter(
        (domain) => !action.payload.domains.includes(domain),
      );
      if (remaining.length > 0) state.dirtyByAccount[key] = remaining;
      else delete state.dirtyByAccount[key];
    },
    markFollowFeedReloadRequired(state) {
      state.followFeedReloadRequired = true;
    },
    clearFollowFeedReloadRequired(state) {
      state.followFeedReloadRequired = false;
    },
    resetProfileRealtime() {
      return initialState;
    },
  },
});

export const {
  markProfileDataDirty,
  clearProfileDataDirty,
  markFollowFeedReloadRequired,
  clearFollowFeedReloadRequired,
  resetProfileRealtime,
} = profileRealtimeSlice.actions;

export default profileRealtimeSlice.reducer;
