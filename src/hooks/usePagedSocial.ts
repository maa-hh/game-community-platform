import { useCallback } from 'react';

import { usePageList } from '@/hooks/usePageList';
import { REPLY_PAGE_SIZE } from '@/components/CommentItem/config';
import {
  fetchPostCommentsPageApi,
  fetchPostRepliesPageApi,
} from '@/service/social';
import type { LatestPostItem, PostComment, PostReply } from '@/types/post';
import type { IGameListItem } from '@/types/game';

const COMMENT_PAGE_SIZE = 20;

export function usePostComments(articleId: string) {
  return usePageList<PostComment>({
    pageSize: COMMENT_PAGE_SIZE,
    enabled: Boolean(articleId),
    resetDeps: [articleId],
    fetchPage: (page, size) =>
      fetchPostCommentsPageApi(articleId, page, size, {
        replyPageSize: REPLY_PAGE_SIZE,
      }),
  });
}

export function useCommentReplies(commentId: string, enabled: boolean) {
  return usePageList<PostReply>({
    pageSize: REPLY_PAGE_SIZE,
    enabled: enabled && Boolean(commentId),
    resetDeps: [commentId, enabled],
    fetchPage: (page, size) => fetchPostRepliesPageApi(commentId, page, size),
  });
}

export function useSearchUsers(keyword: string, enabled: boolean) {
  const fetchPage = useCallback(
    async (page: number, size: number) => {
      const { searchUsersApi } = await import('@/service/account');
      const res = await searchUsersApi({ keyword, page, size });
      if (res.code !== 200) {
        throw Object.assign(new Error(res.message || '搜索失败'), res);
      }
      return {
        code: res.code,
        message: res.message,
        data: res.data || [],
        page,
        size,
        total: Number(res.total ?? 0),
      };
    },
    [keyword],
  );

  return usePageList({
    pageSize: 10,
    enabled: enabled && Boolean(keyword),
    cacheKey: `search-users:${keyword}`,
    resetDeps: [keyword],
    fetchPage,
  });
}

export function useSearchArticles(keyword: string, enabled: boolean) {
  const fetchPage = useCallback(
    async (page: number, size: number) => {
      const { mapSearchArticlesToFeedItems, searchArticlesApi } =
        await import('@/service/search');
      const res = await searchArticlesApi({ keyword, page, size });
      if (res.code !== 200) {
        throw Object.assign(new Error(res.message || '搜索失败'), res);
      }
      const list = await mapSearchArticlesToFeedItems(res.data || []);
      return {
        code: res.code,
        message: res.message,
        data: list,
        page,
        size,
        total: Number(res.total ?? 0),
      };
    },
    [keyword],
  );

  return usePageList<LatestPostItem>({
    pageSize: 10,
    enabled: enabled && Boolean(keyword),
    cacheKey: `search-articles:${keyword}`,
    resetDeps: [keyword],
    fetchPage,
  });
}

export function useSearchGames(keyword: string, enabled: boolean) {
  const fetchPage = useCallback(
    async (page: number, size: number) => {
      const { searchGameIndexApi } = await import('@/service/search');
      const res = await searchGameIndexApi(keyword, { page, size });
      if (res.code !== 200) {
        throw Object.assign(new Error(res.message || '搜索失败'), res);
      }
      return {
        code: res.code,
        message: res.message,
        data: res.data || [],
        page,
        size,
        total: Number(res.total ?? 0),
      };
    },
    [keyword],
  );

  return usePageList<IGameListItem>({
    pageSize: 12,
    enabled: enabled && Boolean(keyword),
    cacheKey: `search-games:${keyword}`,
    resetDeps: [keyword],
    fetchPage,
  });
}
