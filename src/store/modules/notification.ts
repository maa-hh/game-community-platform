import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';

import {
  fetchNotificationCategorySummariesApi,
  fetchNotificationMessagesApi,
  fetchNotificationSummaryApi,
  markNotificationCategoryReadApi,
  type INotificationCategorySummary,
  type INotificationMessage,
  type INotificationSummary,
} from '@/service/notification';
import type { NotificationCategoryKey } from '@/types/notification';
import type { IPageResult } from '@/service/types';
import { resolveNotificationCategory } from '@/utils/notificationCategory';
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
  /** 通知页当前展开的分类（用于 SSE 与已读逻辑协同） */
  activeCategory: NotificationCategoryKey | null;
}

const emptySummary: INotificationSummary = {
  unreadNotificationCount: 0,
  feedUnread: false,
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
  activeCategory: null,
};

export const fetchNotificationSummaryAction = createAsyncThunk(
  'notification/fetchSummary',
  async () => fetchNotificationSummaryApi(),
);

export const fetchNotificationCategoriesAction = createAsyncThunk(
  'notification/fetchCategories',
  async () => fetchNotificationCategorySummariesApi(),
);

export const fetchNotificationBootstrapAction = createAsyncThunk(
  'notification/bootstrap',
  async () => {
    const [summary, categories] = await Promise.all([
      fetchNotificationSummaryApi(),
      fetchNotificationCategorySummariesApi(),
    ]);
    return { summary, categories };
  },
);

/** 仅刷新汇总与分类未读，不清空已展开列表 */
export const fetchNotificationMetaAction = createAsyncThunk(
  'notification/meta',
  async () => {
    const [summary, categories] = await Promise.all([
      fetchNotificationSummaryApi(),
      fetchNotificationCategorySummariesApi(),
    ]);
    return { summary, categories };
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
  async (category: NotificationCategoryKey) => {
    const summary = await markNotificationCategoryReadApi(category);
    const categories = await fetchNotificationCategorySummariesApi();
    return { category, summary, categories };
  },
);

/** 分类已展开时，SSE 到达后强制拉最新列表，避免展示延迟/回放旧通知 */
export const refetchCategoryIfLoadedAction = createAsyncThunk(
  'notification/refetchCategoryIfLoaded',
  async (category: NotificationCategoryKey, { getState, dispatch }) => {
    const state = getState() as { notification: NotificationState };
    const bucket = state.notification.categoryMessages[category];
    if (!bucket?.loaded) return { category, skipped: true as const };
    await dispatch(fetchCategoryMessagesAction({ category })).unwrap();
    return { category, skipped: false as const };
  },
);

const notificationSlice = createSlice({
  name: 'notification',
  initialState,
  reducers: {
    setNotificationSummary(state, action: { payload: INotificationSummary }) {
      state.summary = action.payload;
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
    setActiveNotificationCategory(
      state,
      action: { payload: NotificationCategoryKey | null },
    ) {
      state.activeCategory = action.payload;
    },
    receiveRealtimeNotification(
      state,
      action: {
        payload: {
          message: INotificationMessage;
          summary?: INotificationSummary;
        };
      },
    ) {
      const { message, summary } = action.payload;
      if (summary) {
        state.summary = summary;
      }

      const category = resolveNotificationCategory(message.eventType);
      if (!category) return;

      const bucket =
        state.categoryMessages[category] ?? initialCategoryMessages();
      if (bucket.loaded) {
        const exists = bucket.items.some((item) => item.id === message.id);
        if (!exists) {
          bucket.items = [message, ...bucket.items];
          bucket.total += 1;
        }
      }
      state.categoryMessages[category] = bucket;
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(fetchNotificationSummaryAction.pending, (state) => {
        state.summaryLoading = true;
      })
      .addCase(fetchNotificationSummaryAction.fulfilled, (state, action) => {
        state.summaryLoading = false;
        state.summary = action.payload;
      })
      .addCase(fetchNotificationSummaryAction.rejected, (state) => {
        state.summaryLoading = false;
      })
      .addCase(fetchNotificationBootstrapAction.fulfilled, (state, action) => {
        state.summary = action.payload.summary;
        state.categories = action.payload.categories;
      })
      .addCase(fetchNotificationMetaAction.fulfilled, (state, action) => {
        state.summary = action.payload.summary;
        state.categories = action.payload.categories;
      })
      .addCase(fetchNotificationCategoriesAction.fulfilled, (state, action) => {
        state.categories = action.payload;
      })
      .addCase(fetchCategoryMessagesAction.pending, (state, action) => {
        const category = action.meta.arg.category;
        const bucket =
          state.categoryMessages[category] ?? initialCategoryMessages();
        bucket.loading = !action.meta.arg.append;
        bucket.loadingMore = Boolean(action.meta.arg.append);
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
        state.summary = action.payload.summary;
        state.categories = action.payload.categories;
        const bucket = state.categoryMessages[action.payload.category];
        if (bucket) {
          bucket.items = bucket.items.map((item) => ({
            ...item,
            readStatus: 1,
          }));
        }
      });
  },
});

export const {
  setNotificationSummary,
  patchCategoryUnread,
  resetCategoryMessages,
  resetAllCategoryMessages,
  setActiveNotificationCategory,
  receiveRealtimeNotification,
} = notificationSlice.actions;

export default notificationSlice.reducer;
