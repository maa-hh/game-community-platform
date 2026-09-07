import { createSlice } from '@reduxjs/toolkit';

export interface PostInteraction {
  liked?: boolean;
  favorited?: boolean;
  likeCount?: number;
  favoriteCount?: number;
  likePending?: boolean;
  favoritePending?: boolean;
}

interface IPostInteractionState {
  byAccount: Record<string, Record<string, PostInteraction>>;
}

export interface UpdatePostInteractionPayload {
  accountId?: number | null;
  postId: string;
  patch: PostInteraction;
}

export function getPostInteractionScope(accountId?: number | null): string {
  return String(accountId ?? 'anonymous');
}

const initialState: IPostInteractionState = {
  byAccount: {},
};

const postInteractionSlice = createSlice({
  name: 'postInteraction',
  initialState,
  reducers: {
    updatePostInteraction(
      state,
      action: { payload: UpdatePostInteractionPayload },
    ) {
      const { accountId, postId, patch } = action.payload;
      if (!postId) return;

      const scope = getPostInteractionScope(accountId);
      const accountInteractions = state.byAccount[scope] ?? {};
      const current = accountInteractions[postId] ?? {};
      const next = { ...current, ...patch };
      const changed = Object.keys(next).some(
        (key) =>
          next[key as keyof PostInteraction] !==
          current[key as keyof PostInteraction],
      );
      if (!changed) return;
      accountInteractions[postId] = next;
      state.byAccount[scope] = accountInteractions;
    },
    clearPostInteractions(state) {
      state.byAccount = {};
    },
  },
});

export const { updatePostInteraction, clearPostInteractions } =
  postInteractionSlice.actions;

export default postInteractionSlice.reducer;
