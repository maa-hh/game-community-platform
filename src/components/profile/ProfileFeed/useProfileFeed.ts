import { useCallback, useEffect, useMemo, useState } from 'react';
import { App } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';

import { useArticleOwnerActions } from '@/hooks/useArticleOwnerActions';
import { usePageList } from '@/hooks/usePageList';
import { useRequireLogin } from '@/hooks/useRequireLogin';
import { formatCardTime } from '@/utils/formatTime';
import { mapNumericPostType } from '@/utils/postType';
import {
  mapArticleStatusToTab,
  profileArticleStatusLabel,
} from '@/utils/articleStatus';
import {
  POST_TYPE,
  getAuthorPublishedArticlesByAccountApi,
  getMyArticlesPageForProfileTab,
  type IArticleItem,
} from '@/service/content';
import type { FeedItemData } from '@/types/profile';
import {
  fetchBrowseHistoryApi,
  fetchMyCommentsApi,
  fetchMyFavoritesApi,
  fetchMyLikedArticlesApi,
  fetchMyReceivedLikesApi,
  enrichFeedItemsWithStats,
  resolveRefPostMap,
  togglePostLikeApi,
} from '@/service/social';
import { useAppDispatch, useAppSelector } from '@/store';
import { clearProfileDataDirty } from '@/store/modules/profileRealtime';
import { invalidatePageDataCache } from '@/hooks/pageDataCache';
import { formatApiError } from '@/utils/apiError';
import { buildPostActivityPath } from '@/utils/profileFeed';
import {
  applyProfileOwnerAuthor,
  buildProfileDisplayAuthor,
} from '@/utils/profileAuthor';
import { authorFrom } from '@/utils/mapPost';
import type { PostAuthor } from '@/types/post';
import { resolvePostCardSummary } from '@/utils/postSummary';
import { resolveRepostRefPost } from '@/utils/refPostCard';
import { buildReturnNavigationState } from '@/utils/returnNavigation';
import { isGameRefPostId } from '@/utils/gameRepost';
import {
  EMPTY_PROFILE_DATA_DOMAINS,
  PROFILE_DATA_DOMAIN,
} from '@/types/profileRealtime';
import { invalidateProfileDataCaches } from '@/utils/profileDataCache';
import {
  buildPostDetailNavigationState,
  mapFeedItemToLatestPost,
  mapPostRefToLatestPost,
} from '@/utils/detailNavigation';

import type { IProps } from './types';

const PAGE_SIZE = 20;

function mapMyArticleToFeedItem(
  item: IArticleItem,
  author: PostAuthor,
  refPostMap: Map<string, import('@/types/post').PostRefCard>,
): FeedItemData {
  const postType = mapNumericPostType(item.postType);
  const cover = item.coverUrl?.startsWith('http')
    ? item.coverUrl
    : item.coverUrl;
  const videoUrl = item.videoUrl?.startsWith('http')
    ? item.videoUrl
    : item.videoUrl;
  const refPost =
    postType === 'repost' && item.refArticleId
      ? resolveRepostRefPost(
          item.refArticleId,
          refPostMap.get(item.refArticleId),
        )
      : undefined;

  const summary = resolvePostCardSummary({ summary: item.summary });

  return {
    id: item.publicId,
    author: {
      accountId: author.accountId,
      nickname: author.nickname,
      avatar: author.avatar,
    },
    title: item.title,
    summary,
    content: summary,
    postType,
    images: cover ? [cover] : undefined,
    coverUrl: postType === 'video' ? cover : undefined,
    videoUrl: postType === 'video' ? videoUrl : undefined,
    refPost,
    status: mapArticleStatusToTab(item.status),
    articleStatus: item.status,
    createdAt: formatCardTime(item.updateTime || item.createTime),
    tags: [
      { text: profileArticleStatusLabel(item.status) },
      {
        text:
          item.postType === POST_TYPE.VIDEO
            ? '视频'
            : item.postType === POST_TYPE.REPOST
              ? '转发'
              : '图文',
      },
    ],
  };
}

export function useProfileFeed({
  mainTab,
  postSubTab,
  targetAccountId,
  authorUser,
  isOther = false,
}: IProps) {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const location = useLocation();
  const dispatch = useAppDispatch();
  const { user } = useAppSelector((state) => state.auth);
  const { requireLogin } = useRequireLogin();
  const [likeMap, setLikeMap] = useState<
    Record<string, { liked: boolean; likeCount: number }>
  >({});

  const feedAuthor = authorUser ?? user;
  const profileOwnerAccountId = targetAccountId ?? feedAuthor?.accountId;
  const profileDirtyDomains = useAppSelector((state) =>
    profileOwnerAccountId != null
      ? (state.profileRealtime.dirtyByAccount[String(profileOwnerAccountId)] ??
        EMPTY_PROFILE_DATA_DOMAINS)
      : EMPTY_PROFILE_DATA_DOMAINS,
  );
  const ownerAuthor = useMemo(
    () => (isOther ? null : buildProfileDisplayAuthor(feedAuthor)),
    [feedAuthor, isOther],
  );

  const fetchPagedTab = useCallback(
    async (page: number, size: number) => {
      switch (mainTab) {
        case 'posts': {
          const res =
            targetAccountId != null
              ? await getAuthorPublishedArticlesByAccountApi(
                  targetAccountId,
                  page,
                  size,
                )
              : await getMyArticlesPageForProfileTab(page, size, postSubTab);
          const articles = res.data || [];
          const refPostMap = await resolveRefPostMap(articles);
          const displayAuthor =
            buildProfileDisplayAuthor(isOther ? authorUser : feedAuthor) ??
            authorFrom(
              feedAuthor?.accountId ?? 0,
              feedAuthor?.username,
              feedAuthor?.avatar,
            );
          const feeds = articles.map((item) =>
            mapMyArticleToFeedItem(item, displayAuthor, refPostMap),
          );
          const enriched = await enrichFeedItemsWithStats(feeds);
          return {
            ...res,
            data: applyProfileOwnerAuthor(enriched, ownerAuthor),
          };
        }
        case 'history': {
          const res = await fetchBrowseHistoryApi(page, size);
          return {
            ...res,
            data: applyProfileOwnerAuthor(res.data || [], ownerAuthor),
          };
        }
        case 'liked': {
          const res = await fetchMyLikedArticlesApi(page, size);
          return {
            ...res,
            data: applyProfileOwnerAuthor(res.data || [], ownerAuthor),
          };
        }
        case 'received': {
          const res = await fetchMyReceivedLikesApi(page, size);
          return {
            ...res,
            data: applyProfileOwnerAuthor(res.data || [], ownerAuthor),
          };
        }
        case 'favorites': {
          const res = await fetchMyFavoritesApi(page, size);
          return {
            ...res,
            data: applyProfileOwnerAuthor(res.data || [], ownerAuthor),
          };
        }
        case 'comments': {
          const res = await fetchMyCommentsApi(page, size);
          return {
            ...res,
            data: applyProfileOwnerAuthor(res.data || [], ownerAuthor),
          };
        }
        default:
          return {
            code: 200,
            message: 'success',
            data: [],
            page,
            size,
            total: 0,
          };
      }
    },
    [
      feedAuthor,
      isOther,
      mainTab,
      ownerAuthor,
      postSubTab,
      targetAccountId,
      authorUser,
    ],
  );

  const profileFeedCacheKey = `profile-feed:${isOther ? 'other' : 'self'}:${targetAccountId ?? feedAuthor?.accountId ?? 'unknown'}:${mainTab}:${mainTab === 'posts' ? postSubTab : 'all'}`;
  const pagedList = usePageList<FeedItemData>({
    pageSize: PAGE_SIZE,
    cacheKey: profileFeedCacheKey,
    resetDeps: [mainTab, postSubTab, targetAccountId],
    fetchPage: fetchPagedTab,
  });

  const feedDomain =
    mainTab === 'posts'
      ? PROFILE_DATA_DOMAIN.POSTS
      : mainTab === 'history'
        ? PROFILE_DATA_DOMAIN.HISTORY
        : mainTab === 'liked'
          ? PROFILE_DATA_DOMAIN.LIKED
          : mainTab === 'received'
            ? PROFILE_DATA_DOMAIN.RECEIVED
            : mainTab === 'favorites'
              ? PROFILE_DATA_DOMAIN.FAVORITES
              : PROFILE_DATA_DOMAIN.COMMENTS;

  useEffect(() => {
    if (
      isOther ||
      location.pathname !== '/profile' ||
      profileOwnerAccountId == null ||
      !profileDirtyDomains.includes(feedDomain)
    ) {
      return;
    }

    dispatch(
      clearProfileDataDirty({
        accountId: profileOwnerAccountId,
        domains: [feedDomain],
      }),
    );
  }, [
    dispatch,
    feedDomain,
    isOther,
    location.pathname,
    profileDirtyDomains,
    profileOwnerAccountId,
  ]);

  const { goEdit, buildOwnerActionBarItems } = useArticleOwnerActions();

  const list = useMemo(
    () =>
      pagedList.items.map((item) => {
        const articleId = item.targetArticleId || item.id;
        const override = articleId ? likeMap[articleId] : undefined;
        if (!override) return item;
        return {
          ...item,
          liked: override.liked,
          likeCount: override.likeCount,
        };
      }),
    [pagedList.items, likeMap],
  );

  const handleLike = useCallback(
    async (item: FeedItemData) => {
      if (!requireLogin()) return;

      const articleId = item.targetArticleId || item.id;
      if (!articleId) return;

      const current = likeMap[articleId] ?? {
        liked: Boolean(item.liked),
        likeCount: item.likeCount ?? 0,
      };
      const nextLiked = !current.liked;
      const snapshot = current;

      setLikeMap((prev) => ({
        ...prev,
        [articleId]: {
          liked: nextLiked,
          likeCount: Math.max(0, current.likeCount + (nextLiked ? 1 : -1)),
        },
      }));

      try {
        const res = await togglePostLikeApi(articleId, nextLiked);
        setLikeMap((prev) => ({
          ...prev,
          [articleId]: {
            liked: res.data.liked,
            likeCount: res.data.likeCount,
          },
        }));
        invalidatePageDataCache(profileFeedCacheKey);
        invalidateProfileDataCaches(user?.accountId, [
          PROFILE_DATA_DOMAIN.LIKED,
          PROFILE_DATA_DOMAIN.RECEIVED,
        ]);
      } catch (err) {
        message.error(formatApiError('操作失败', err));
        setLikeMap((prev) => {
          const copy = { ...prev };
          if (snapshot.liked === Boolean(item.liked)) {
            delete copy[articleId];
          } else {
            copy[articleId] = snapshot;
          }
          return copy;
        });
      }
    },
    [likeMap, message, profileFeedCacheKey, requireLogin, user?.accountId],
  );

  const handleRefresh = useCallback(async () => {
    try {
      await pagedList.reload();
    } catch {
      message.error(formatApiError('刷新失败', new Error()));
    }
  }, [message, pagedList]);

  const handleItemClick = useCallback(
    (item: FeedItemData) => {
      const activityPath = buildPostActivityPath(item);
      if (activityPath) {
        const preview = item.refPost
          ? mapPostRefToLatestPost(item.refPost, item.targetArticleId)
          : undefined;
        navigate(activityPath, {
          state: preview
            ? buildPostDetailNavigationState(location, preview)
            : undefined,
        });
        return;
      }
      if (!item.id || isGameRefPostId(item.id)) return;

      if (mainTab === 'posts' && item.articleStatus != null) {
        const tab = mapArticleStatusToTab(item.articleStatus);
        if (tab !== 'published') {
          if (item.postType === 'repost') {
            message.info('转发动态已下架，请重新转发或删除');
            return;
          }
          goEdit(item.id);
          return;
        }
      }
      const preview = mapFeedItemToLatestPost(item);
      navigate(`/post/${item.id}`, {
        state: preview
          ? buildPostDetailNavigationState(location, preview)
          : buildReturnNavigationState(location),
      });
    },
    [location, mainTab, goEdit, message, navigate],
  );

  const handleGoPublish = useCallback(() => {
    navigate('/post/editor');
  }, [navigate]);

  const navigateToPost = useCallback(
    (path: string, item?: FeedItemData) => {
      const preview = item?.refPost
        ? mapPostRefToLatestPost(item.refPost, item.targetArticleId)
        : item
          ? mapFeedItemToLatestPost(item)
          : undefined;
      navigate(path, {
        state: preview
          ? buildPostDetailNavigationState(location, preview)
          : buildReturnNavigationState(location),
      });
    },
    [location, navigate],
  );

  return {
    mainTab,
    isOther,
    list,
    loading: pagedList.loading,
    loadingMore: pagedList.loadingMore,
    refreshing: pagedList.refreshing,
    hasMore: pagedList.hasMore,
    sentinelRef: pagedList.sentinelRef,
    buildOwnerActionBarItems,
    handleLike,
    handleRefresh,
    handleItemClick,
    handleGoPublish,
    navigateToPost,
  };
}
