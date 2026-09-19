import { createApi, fakeBaseQuery } from '@reduxjs/toolkit/query/react';

import { fetchFollowFeedApi, fetchLatestPostsPageApi } from '@/service/social';
import type { LatestPostItem } from '@/types/post';
import { flattenFeedPages } from './feedCache';

export const COMMUNITY_FEED_PAGE_SIZE = 20;

export interface FeedPage {
  items: LatestPostItem[];
  nextCursor?: string;
  hasMore: boolean;
}

export type CommunityFeedPage = FeedPage;

export interface CommunityFeedQueryArg {
  /** 账号维度只用于缓存隔离，实际身份仍由请求层 token 决定。 */
  accountId: number | null;
}

export interface FollowFeedQueryArg {
  accountId: number | null;
  postType?: number;
}

interface ServerQueryError {
  status: 'CUSTOM_ERROR';
  data: string;
}

function toQueryError(error: unknown): ServerQueryError {
  if (error instanceof Error && error.message) {
    return { status: 'CUSTOM_ERROR', data: error.message };
  }
  if (error && typeof error === 'object' && 'message' in error) {
    return {
      status: 'CUSTOM_ERROR',
      data: String((error as { message?: unknown }).message || '请求失败'),
    };
  }
  return { status: 'CUSTOM_ERROR', data: '请求失败' };
}

/**
 * 服务端状态统一入口。
 * 先迁移社区信息流，后续按业务域向这里追加 endpoint，避免每个页面
 * 各自维护一套缓存、刷新和并发控制。
 */
export const serverApi = createApi({
  reducerPath: 'serverApi',
  baseQuery: fakeBaseQuery<ServerQueryError>(),
  tagTypes: ['CommunityFeed', 'FollowFeed'],
  endpoints: (build) => ({
    communityFeed: build.infiniteQuery<
      CommunityFeedPage,
      CommunityFeedQueryArg,
      string | undefined
    >({
      infiniteQueryOptions: {
        initialPageParam: undefined,
        maxPages: 10,
        refetchCachedPages: true,
        getNextPageParam: (lastPage) =>
          lastPage.hasMore ? lastPage.nextCursor : undefined,
      },
      queryFn: async ({ pageParam }) => {
        try {
          const response = await fetchLatestPostsPageApi({
            lastId: pageParam,
            size: COMMUNITY_FEED_PAGE_SIZE,
          });
          const items = response.data || [];

          return {
            data: {
              items,
              nextCursor: items.at(-1)?.id,
              hasMore: items.length >= COMMUNITY_FEED_PAGE_SIZE,
            },
          };
        } catch (error) {
          return { error: toQueryError(error) };
        }
      },
      providesTags: (_result, _error, arg) => [
        { type: 'CommunityFeed', id: String(arg.accountId ?? 'anonymous') },
      ],
    }),
    followFeed: build.infiniteQuery<
      FeedPage,
      FollowFeedQueryArg,
      string | undefined
    >({
      infiniteQueryOptions: {
        initialPageParam: undefined,
        maxPages: 10,
        refetchCachedPages: true,
        getNextPageParam: (lastPage) =>
          lastPage.hasMore ? lastPage.nextCursor : undefined,
      },
      queryFn: async ({ queryArg, pageParam }) => {
        // 关注流是登录后数据；游客保留页面和分区 Tab，但不触发受保护请求。
        if (queryArg.accountId == null) {
          return {
            data: {
              items: [],
              hasMore: false,
            },
          };
        }
        try {
          const response = await fetchFollowFeedApi({
            before: pageParam,
            size: COMMUNITY_FEED_PAGE_SIZE,
            postType: queryArg.postType,
          });
          const items = response.data || [];

          return {
            data: {
              items,
              nextCursor: items.at(-1)?.sortTime,
              hasMore: items.length >= COMMUNITY_FEED_PAGE_SIZE,
            },
          };
        } catch (error) {
          return { error: toQueryError(error) };
        }
      },
      providesTags: (_result, _error, arg) => [
        {
          type: 'FollowFeed',
          id: `${arg.accountId ?? 'anonymous'}:${arg.postType ?? 'all'}`,
        },
      ],
    }),
  }),
});

export const { useCommunityFeedInfiniteQuery, useFollowFeedInfiniteQuery } =
  serverApi;

export { flattenFeedPages };
