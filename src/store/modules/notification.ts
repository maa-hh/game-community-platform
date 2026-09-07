import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';

import {
  fetchNotificationCategorySummariesApi,
  fetchNotificationMessagesApi,
  fetchNotificationSummaryApi,
  markFeedReadApi,
  markNotificationCategoryReadApi,
  type INotificationCategorySummary,
  type INotificationMessage,
  type INotificationSummary,
} from '@/service/notification';
import type { NotificationCategoryKey } from '@/types/notification';
import type { IPageResult } from '@/service/types';
import { enrichNotificationMessages } from '@/utils/enrichNotificationMessages';

interface CategoryMessagesState {
  items: INotificationMessage[];
  page: number;
  total: number;
  hasMore: boolean;
  loading: boolean;
  loadingMore: boolean;
  loaded: boolean;
  error?: boolean;
}

interface NotificationState {
  summary: INotificationSummary;
  categories: INotificationCategorySummary[];
  categoryMessages: Partial<
    Record<NotificationCategoryKey, CategoryMessagesState>
  >;
  summaryLoading: boolean;
  metaLoaded: boolean;
  /** 防止 SSE 到达后，被更早发起但更晚返回的元数据请求覆盖。 */
  realtimeRevision: number;
  /** SSE 只标记分类有更新，由通知页决定是否刷新。 */
  categoryRefreshRequired: Partial<Record<NotificationCategoryKey, boolean>>;
}

const emptySummary: INotificationSummary = {
  unreadNotificationCount: 0,
  feedUnread: false,
  feedUnreadCount: 0,
};

const initialCategoryMessages = (): CategoryMessagesState => ({
  items: [],
  page: 0,
  total: 0,
  hasMore: true,
  loading: false,
  loadingMore: false,
  loaded: false,
});

const initialState: NotificationState = {
  summary: emptySummary,
  categories: [],
  categoryMessages: {},
  summaryLoading: false,
  metaLoaded: false,
  realtimeRevision: 0,
  categoryRefreshRequired: {},
};

export const fetchNotificationSummaryAction = createAsyncThunk(
  'notification/fetchSummary',
  async (_, { getState }) => {
    const state = getState() as { notification: NotificationState };
    const realtimeRevision = state.notification.realtimeRevision;
    const summary = await fetchNotificationSummaryApi();
    return { summary, realtimeRevision };
  },
);

export const fetchNotificationCategoriesAction = createAsyncThunk(
  'notification/fetchCategories',
  async (_, { getState }) => {
    const state = getState() as { notification: NotificationState };
    const realtimeRevision = state.notification.realtimeRevision;
    const categories = await fetchNotificationCategorySummariesApi();
    return { categories, realtimeRevision };
  },
);

export const fetchNotificationBootstrapAction = createAsyncThunk(
  'notification/bootstrap',
  async (_, { getState }) => {
    const state = getState() as { notification: NotificationState };
    const realtimeRevision = state.notification.realtimeRevision;
    const [summary, categories] = await Promise.all([
      fetchNotificationSummaryApi(),
      fetchNotificationCategorySummariesApi(),
    ]);
    return { summary, categories, realtimeRevision };
  },
);

/** 仅刷新汇总与分类未读，不清空已展开列表 */
export const fetchNotificationMetaAction = createAsyncThunk(
  'notification/meta',
  async (_, { getState }) => {
    const state = getState() as { notification: NotificationState };
    const realtimeRevision = state.notification.realtimeRevision;
    const [summary, categories] = await Promise.all([
      fetchNotificationSummaryApi(),
      fetchNotificationCategorySummariesApi(),
    ]);
    return { summary, categories, realtimeRevision };
  },
);

export const fetchCategoryMessagesAction = createAsyncThunk(
  'notification/fetchCategoryMessages',
  async (
    payload: {
      category: NotificationCategoryKey;
      page?: number;
      append?: boolean;
    },
    { getState },
  ) => {
    const state = getState() as { notification: NotificationState };
    const current = state.notification.categoryMessages[payload.category];
    const page =
      payload.page ?? (payload.append ? (current?.page ?? 0) + 1 : 1);
    const res: IPageResult<INotificationMessage> =
      await fetchNotificationMessagesApi({
        category: payload.category,
        page,
        size: 20,
      });
    const items = await enrichNotificationMessages(res.data || []);
    return {
      category: payload.category,
      page,
      append: Boolean(payload.append),
      items,
      total: Number(res.total ?? 0),
    };
  },
);

export const markCategoryReadAction = createAsyncThunk(
  'notification/markCategoryRead',
  async (category: NotificationCategoryKey, { getState }) => {
    const state = getState() as { notification: NotificationState };
    const realtimeRevision = state.notification.realtimeRevision;
    const summary = await markNotificationCategoryReadApi(category);
    const categories = await fetchNotificationCategorySummariesApi();
    return { category, summary, categories, realtimeRevision };
  },
);

export const markFeedReadAction = createAsyncThunk(
  'notification/markFeedRead',
  async (_, { getState }) => {
    const state = getState() as { notification: NotificationState };
    const realtimeRevision = state.notification.realtimeRevision;
    const summary = await markFeedReadApi();
    return { summary, realtimeRevision };
  },
);

const notificationSlice = createSlice({
  name: 'notification',
  initialState,
  reducers: {
    setNotificationSummary(state, action: { payload: INotificationSummary }) {
      state.summary = action.payload;
      state.realtimeRevision += 1;
    },
    patchNotificationSummary(
      state,
      action: { payload: Partial<INotificationSummary> },
    ) {
      state.summary = { ...state.summary, ...action.payload };
      state.realtimeRevision += 1;
    },
    incrementNotificationUnread(state) {
      state.summary.unreadNotificationCount += 1;
      state.realtimeRevision += 1;
    },
    incrementFeedUnread(state) {
      state.summary.feedUnread = true;
      state.summary.feedUnreadCount += 1;
      state.realtimeRevision += 1;
    },
    patchCategoryUnread(
      state,
      action: {
        payload: { category: NotificationCategoryKey; unreadCount: number };
      },
    ) {
      const item = state.categories.find(
        (c) => c.category === action.payload.category,
      );
      if (item) {
        item.unreadCount = action.payload.unreadCount;
      }
    },
    resetCategoryMessages(state, action: { payload: NotificationCategoryKey }) {
      state.categoryMessages[action.payload] = initialCategoryMessages();
    },
    resetAllCategoryMessages(state) {
      state.categoryMessages = {};
    },
    resetNotificationState() {
      return initialState;
    },
    markCategoryRefreshRequired(
      state,
      action: {
        payload: {
          category: NotificationCategoryKey;
        };
      },
    ) {
      const { category } = action.payload;
      state.categoryRefreshRequired[category] = true;
      state.realtimeRevision += 1;
      const categorySummary = state.categories.find(
        (item) => item.category === category,
      );
      if (categorySummary) {
        categorySummary.unreadCount += 1;
      }
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(fetchNotificationSummaryAction.pending, (state) => {
        state.summaryLoading = true;
      })
      .addCase(fetchNotificationSummaryAction.fulfilled, (state, action) => {
        state.summaryLoading = false;
        if (action.payload.realtimeRevision === state.realtimeRevision) {
          state.summary = action.payload.summary;
        }
      })
      .addCase(fetchNotificationSummaryAction.rejected, (state) => {
        state.summaryLoading = false;
      })
      .addCase(fetchNotificationBootstrapAction.pending, (state) => {
        state.summaryLoading = true;
      })
      .addCase(fetchNotificationBootstrapAction.fulfilled, (state, action) => {
        state.summaryLoading = false;
        if (action.payload.realtimeRevision === state.realtimeRevision) {
          state.summary = action.payload.summary;
          state.categories = action.payload.categories;
          state.metaLoaded = true;
        }
      })
      .addCase(fetchNotificationBootstrapAction.rejected, (state) => {
        state.summaryLoading = false;
      })
      .addCase(fetchNotificationMetaAction.pending, (state) => {
        state.summaryLoading = true;
      })
      .addCase(fetchNotificationMetaAction.fulfilled, (state, action) => {
        state.summaryLoading = false;
        if (action.payload.realtimeRevision === state.realtimeRevision) {
          state.summary = action.payload.summary;
          state.categories = action.payload.categories;
          state.metaLoaded = true;
        }
      })
      .addCase(fetchNotificationMetaAction.rejected, (state) => {
        state.summaryLoading = false;
      })
      .addCase(fetchNotificationCategoriesAction.fulfilled, (state, action) => {
        if (action.payload.realtimeRevision === state.realtimeRevision) {
          state.categories = action.payload.categories;
        }
      })
      .addCase(fetchCategoryMessagesAction.pending, (state, action) => {
        const category = action.meta.arg.category;
        const bucket =
          state.categoryMessages[category] ?? initialCategoryMessages();
        bucket.loading = !action.meta.arg.append;
        bucket.loadingMore = Boolean(action.meta.arg.append);
        bucket.error = false;
        state.categoryMessages[category] = bucket;
      })
      .addCase(fetchCategoryMessagesAction.fulfilled, (state, action) => {
        const { category, page, append, items, total } = action.payload;
        const bucket =
          state.categoryMessages[category] ?? initialCategoryMessages();
        bucket.page = page;
        bucket.total = total;
        bucket.items = append ? [...bucket.items, ...items] : items;
        bucket.hasMore = bucket.items.length < total;
        bucket.loading = false;
        bucket.loadingMore = false;
        bucket.loaded = true;
        bucket.error = false;
        delete state.categoryRefreshRequired[category];
        state.categoryMessages[category] = bucket;
      })
      .addCase(fetchCategoryMessagesAction.rejected, (state, action) => {
        const category = action.meta.arg.category;
        const bucket =
          state.categoryMessages[category] ?? initialCategoryMessages();
        bucket.loading = false;
        bucket.loadingMore = false;
        bucket.loaded = true;
        bucket.error = true;
        state.categoryMessages[category] = bucket;
      })
      .addCase(markCategoryReadAction.fulfilled, (state, action) => {
        if (action.payload.realtimeRevision === state.realtimeRevision) {
          state.summary = action.payload.summary;
          state.categories = action.payload.categories;
        }
        const bucket = state.categoryMessages[action.payload.category];
        if (bucket) {
          bucket.items = bucket.items.map((item) => ({
            ...item,
            readStatus: 1,
          }));
        }
      })
      .addCase(markFeedReadAction.fulfilled, (state, action) => {
        if (action.payload.realtimeRevision === state.realtimeRevision) {
          state.summary = action.payload.summary;
        }
      });
  },
});

export const {
  setNotificationSummary,
  patchNotificationSummary,
  incrementNotificationUnread,
  incrementFeedUnread,
  patchCategoryUnread,
  resetCategoryMessages,
  resetAllCategoryMessages,
  resetNotificationState,
  markCategoryRefreshRequired,
} = notificationSlice.actions;

export default notificationSlice.reducer;
