import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import {
  useLocation,
  useNavigate,
  useParams,
  useSearchParams,
} from 'react-router-dom';
import { Button, message } from 'antd';
import { FlagOutlined } from '@ant-design/icons';

import PageLoading from '@/base-ui/PageLoading';
import { REPLY_PAGE_SIZE } from '@/components/CommentItem/config';
import SurfaceCard from '@/base-ui/SurfaceCard';
import CommentSection from '@/components/CommentSection';
import FeedPanel from '@/components/FeedPanel';
import PostActionBar from '@/components/PostActionBar';
import PostBottomBar from '@/components/PostBottomBar';
import ShareSheet from '@/components/ShareSheet';
import ReportModal from '@/components/ReportModal';
import { useArticleOwnerActions } from '@/hooks/useArticleOwnerActions';
import { useGoBack } from '@/hooks/useGoBack';
import { usePostComments } from '@/hooks/usePagedSocial';
import { useRequireLogin } from '@/hooks/useRequireLogin';
import { invalidateProfileDataCaches } from '@/utils/profileDataCache';
import { PROFILE_DATA_DOMAIN } from '@/types/profileRealtime';
import { usePostLikeAction } from '@/hooks/usePostLikeAction';
import { useOptimisticAction } from '@/hooks/useOptimisticAction';
import { useProfileFollowingSync } from '@/hooks/useProfileFollowingSync';
import { useReportModal } from '@/hooks/useReportModal';
import { useUserDecorations } from '@/hooks/useUserDecorations';
import {
  usePostInteraction,
  usePostInteractionActions,
} from '@/hooks/usePostInteraction';
import PostBody from '@/views/PostDetail/components/PostBody';
import PostDetailTopBar from '@/views/PostDetail/components/PostDetailTopBar';
import type { PostComment, PostDetailData } from '@/types/post';
import {
  createCommentApi,
  fetchPostCommentDetailApi,
  fetchPostDetailApi,
  fetchPostReplyDetailApi,
  fetchPostRepliesPageApi,
  toggleFavoriteApi,
  toggleFollowByAccountApi,
} from '@/service/social';
import { ARTICLE_STATUS } from '@/service/content';
import { formatApiError } from '@/utils/apiError';
import { resolveAvatarFrameAsset } from '@/constants/avatarFrameCatalog';
import { mapContentPostTypeToNumeric } from '@/utils/postType';
import { canGoBackInApp } from '@/utils/returnNavigation';
import { readPostDetailPreview } from '@/utils/detailNavigation';

import './style.less';

function arePostDetailsEqual(
  previous: PostDetailData | null,
  next: PostDetailData,
): boolean {
  return previous != null && JSON.stringify(previous) === JSON.stringify(next);
}

function mergeStableImages(
  previewImages?: string[],
  detailImages?: string[],
): string[] | undefined {
  if (!detailImages?.length) return previewImages;
  if (!previewImages?.length) return detailImages;

  // 列表与详情通常共享第一张封面。保留预览中的封面 URL，避免详情响应
  // 到达后替换掉已经开始加载的图片；详情新增图片再追加到末尾。
  if (previewImages[0] !== detailImages[0]) return detailImages;

  const seen = new Set(previewImages);
  return [
    ...previewImages,
    ...detailImages.slice(1).filter((url) => {
      if (seen.has(url)) return false;
      seen.add(url);
      return true;
    }),
  ];
}

function mergePostPreview(
  preview: PostDetailData | null,
  detail: PostDetailData,
): PostDetailData {
  if (!preview || preview.id !== detail.id) return detail;

  return {
    ...preview,
    ...detail,
    content: detail.content || preview.content,
    contentHtml: detail.contentHtml || preview.contentHtml,
    images: mergeStableImages(preview.images, detail.images),
    bodyImages: detail.bodyImages || preview.bodyImages,
    coverUrl: detail.coverUrl || preview.coverUrl,
    videoUrl: detail.videoUrl || preview.videoUrl,
  };
}

function PostDetail() {
  const { id = '' } = useParams();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const commentHighlight = useMemo(
    () => ({
      commentId: searchParams.get('commentId') || undefined,
      replyId: searchParams.get('replyId') || undefined,
    }),
    [searchParams],
  );
  const navigate = useNavigate();
  const goBack = useGoBack();
  const { user, requireLogin } = useRequireLogin();

  const postPreview = useMemo(
    () => readPostDetailPreview(location.state, id),
    [id, location.state],
  );

  const [loading, setLoading] = useState(!postPreview);
  const [refreshing, setRefreshing] = useState(Boolean(postPreview));
  const [loadError, setLoadError] = useState<string | null>(null);
  const [postState, setPostState] = useState<PostDetailData | null>(
    postPreview,
  );
  const interaction = usePostInteraction(id);
  const { updateInteraction, invalidateCommunityFeed } =
    usePostInteractionActions();
  const optimisticIdRef = useRef(0);
  const { run: runOptimisticAction, isPending } = useOptimisticAction();
  const syncProfileFollowing = useProfileFollowingSync();
  const displayedPost = useMemo(() => {
    if (!postState) return null;
    return {
      ...postState,
      stats: {
        ...postState.stats,
        ...(interaction.liked != null ? { liked: interaction.liked } : {}),
        ...(interaction.favorited != null
          ? { favorited: interaction.favorited }
          : {}),
        ...(interaction.likeCount != null
          ? { likeCount: interaction.likeCount }
          : {}),
        ...(interaction.favoriteCount != null
          ? { favoriteCount: interaction.favoriteCount }
          : {}),
      },
    };
  }, [interaction, postState]);
  const commentPager = usePostComments(id);
  const { items: normalComments, setItems: setNormalComments } = commentPager;
  const [targetComment, setTargetComment] = useState<PostComment | null>(null);
  const comments = useMemo(
    () =>
      targetComment
        ? [
            targetComment,
            ...normalComments
              .filter((comment) => comment.id !== targetComment.id)
              .map((comment) =>
                commentHighlight.replyId
                  ? {
                      ...comment,
                      replies: comment.replies.filter(
                        (reply) => reply.id !== commentHighlight.replyId,
                      ),
                    }
                  : comment,
              ),
          ]
        : normalComments,
    [commentHighlight.replyId, normalComments, targetComment],
  );
  const setComments = useCallback<
    React.Dispatch<React.SetStateAction<PostComment[]>>
  >(
    (nextValue) => {
      if (!targetComment) {
        setNormalComments(nextValue);
        return;
      }
      const current = [
        targetComment,
        ...normalComments.filter((comment) => comment.id !== targetComment.id),
      ];
      const nextComments =
        typeof nextValue === 'function' ? nextValue(current) : nextValue;
      const nextTarget = nextComments.find(
        (comment) => comment.id === targetComment.id,
      );
      setTargetComment(nextTarget || null);
      setNormalComments(
        nextComments.filter((comment) => comment.id !== targetComment.id),
      );
    },
    [normalComments, setNormalComments, targetComment],
  );
  const [shareOpen, setShareOpen] = useState(false);
  const [danmakuReportResetKey, setDanmakuReportResetKey] = useState(0);
  const commentSubmitting = isPending('post-comment-create');
  const { reportOpen, reportTarget, openReport, closeReport } =
    useReportModal();

  const postRef = useRef<PostDetailData | null>(null);

  const handleLike = usePostLikeAction(
    id,
    (next) => {
      setPostState((current) =>
        current
          ? {
              ...current,
              stats: {
                ...current.stats,
                liked: next.liked,
                likeCount: next.likeCount,
              },
            }
          : current,
      );
    },
    () => ({
      liked: Boolean(postRef.current?.stats.liked),
      likeCount: postRef.current?.stats.likeCount ?? 0,
    }),
  );

  postRef.current = displayedPost;

  const locationState =
    typeof location.state === 'object' && location.state !== null
      ? (location.state as { returnTo?: unknown })
      : null;
  const returnTo =
    typeof locationState?.returnTo === 'string' ? locationState.returnTo : null;

  const handleBack = useCallback(() => {
    if (document.activeElement instanceof HTMLElement) {
      document.activeElement.blur();
    }
    if (returnTo && canGoBackInApp()) {
      navigate(-1);
      return;
    }
    if (returnTo) {
      navigate(returnTo, {
        replace: true,
      });
      return;
    }
    if (canGoBackInApp()) {
      navigate(-1);
      return;
    }
    goBack();
  }, [goBack, navigate, returnTo]);

  const { get: getAuthorDecoration } = useUserDecorations([
    postState?.author.accountId,
  ]);
  const authorDecoration = getAuthorDecoration(postState?.author.accountId);
  const authorFrameUrl = resolveAvatarFrameAsset(
    authorDecoration?.avatarFrame?.code,
    authorDecoration?.avatarFrame?.assetJson,
  )?.frameUrl;

  useEffect(() => {
    const commentId = commentHighlight.commentId;
    if (!commentId) {
      setTargetComment(null);
      return;
    }

    let cancelled = false;
    setTargetComment(null);
    const hydrateTarget = async () => {
      try {
        const comment = await fetchPostCommentDetailApi(id, commentId);
        if (!comment || cancelled) return;

        let replies: PostComment['replies'] = [];
        let replyPage = 0;
        const replyPageSize = REPLY_PAGE_SIZE;
        let replyCount = comment.replyCount;
        if (commentHighlight.replyId) {
          const [reply, replyPageResult] = await Promise.all([
            fetchPostReplyDetailApi(commentHighlight.replyId).catch(() => null),
            fetchPostRepliesPageApi(commentId, 1, replyPageSize).catch(
              () => null,
            ),
          ]);
          const firstPageReplies = replyPageResult?.data || [];
          const mergedReplies = reply
            ? [reply, ...firstPageReplies]
            : firstPageReplies;
          const seen = new Set<string>();
          replies = mergedReplies.filter((item) => {
            if (seen.has(item.id)) return false;
            seen.add(item.id);
            return true;
          });
          if (replyPageResult) {
            replyPage = Number(replyPageResult.page ?? 1);
            replyCount = Math.max(
              replyCount,
              Number(replyPageResult.total ?? 0),
              replies.length,
            );
          }
        } else if (comment.replyCount > 0) {
          const replyPageResult = await fetchPostRepliesPageApi(
            commentId,
            1,
            replyPageSize,
          );
          replies = replyPageResult.data || [];
          replyPage = Number(replyPageResult.page ?? 1);
        }

        if (cancelled) return;
        setTargetComment({
          ...comment,
          replies,
          replyPage,
          replyPageSize,
          replyCount,
        });
      } catch {
        // 目标内容被删除或暂时不可见时，正常评论流仍然可用。
      }
    };
    void hydrateTarget();
    return () => {
      cancelled = true;
    };
  }, [commentHighlight.commentId, commentHighlight.replyId, id]);

  const targetDanmakuId = useMemo(() => {
    const value = Number(searchParams.get('danmakuId') || '');
    return Number.isSafeInteger(value) && value > 0 ? value : undefined;
  }, [searchParams]);

  const reloadDetail = useCallback(async () => {
    const detailRes = await fetchPostDetailApi(id);
    if (!detailRes.data) {
      setPostState(null);
      setLoadError('帖子不存在、已删除或已下架');
      return false;
    }

    const detail = detailRes.data;
    const isOwner =
      user?.accountId != null &&
      Number(user.accountId) === Number(detail.author.accountId);
    if (
      detail.status != null &&
      detail.status !== ARTICLE_STATUS.PUBLISHED &&
      !isOwner
    ) {
      setPostState(null);
      setLoadError('帖子已下架或暂不可见');
      return false;
    }

    setLoadError(null);
    setPostState((current) => {
      const merged = mergePostPreview(current, detail);
      return arePostDetailsEqual(current, merged) ? current : merged;
    });
    updateInteraction(id, {
      liked: detail.stats.liked,
      favorited: detail.stats.favorited,
      likeCount: detail.stats.likeCount,
      favoriteCount: detail.stats.favoriteCount,
    });
    return true;
  }, [id, updateInteraction, user?.accountId]);

  const reloadAll = useCallback(async () => {
    await Promise.all([reloadDetail(), commentPager.reload()]);
  }, [commentPager, reloadDetail]);

  const handleRefresh = useCallback(async () => {
    try {
      await reloadAll();
    } catch (err) {
      message.error(formatApiError('刷新失败', err));
    }
  }, [reloadAll]);

  const { buildOwnerMenuItems } = useArticleOwnerActions({
    onPublished: () => reloadDetail(),
    onUnpublished: () =>
      navigate('/profile', { state: { postSubTab: 'draft' as const } }),
    onDeleted: () => navigate('/profile', { replace: true }),
  });

  useEffect(() => {
    let cancelled = false;
    if (postPreview) {
      setPostState(postPreview);
      updateInteraction(id, {
        liked: postPreview.stats.liked,
        likeCount: postPreview.stats.likeCount,
        favorited: postPreview.stats.favorited,
        favoriteCount: postPreview.stats.favoriteCount,
      });
    } else {
      setPostState(null);
    }
    setLoadError(null);
    setLoading(!postPreview);
    setRefreshing(Boolean(postPreview));
    (async () => {
      try {
        await reloadDetail();
      } finally {
        if (!cancelled) {
          setLoading(false);
          setRefreshing(false);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [id, postPreview, reloadDetail, updateInteraction]);

  const handleReportDanmaku = useCallback(
    (messageId: string) => {
      if (!requireLogin()) {
        setDanmakuReportResetKey((current) => current + 1);
        return;
      }
      openReport('danmaku', messageId, '举报弹幕');
    },
    [openReport, requireLogin],
  );

  const handleReportClose = useCallback(() => {
    if (reportTarget?.type === 'danmaku') {
      setDanmakuReportResetKey((current) => current + 1);
    }
    closeReport();
  }, [closeReport, reportTarget?.type]);

  if (loading) {
    return (
      <div className="post-detail">
        <PageLoading full />
      </div>
    );
  }

  if (loadError || !displayedPost) {
    return (
      <div className="post-detail post-detail--unavailable">
        <div className="post-detail__align-track">
          <SurfaceCard className="post-detail__unavailable-card">
            <h1 className="post-detail__unavailable-title">{loadError}</h1>
            <p className="post-detail__unavailable-desc">
              内容可能已被作者删除或下架，请返回后刷新列表再试。
            </p>
            <Button type="primary" onClick={handleBack}>
              返回
            </Button>
          </SurfaceCard>
        </div>
      </div>
    );
  }

  const postData = displayedPost;

  const isVideo = postData.postType === 'video';
  const isOwner =
    user?.accountId != null &&
    Number(user.accountId) === postData.author.accountId;

  const handleFollow = async () => {
    if (!requireLogin() || !postData) return;
    if (isPending('post-follow')) return;
    const next = !postData.followedAuthor;
    await runOptimisticAction('post-follow', {
      apply: () =>
        setPostState((current) =>
          current ? { ...current, followedAuthor: next } : current,
        ),
      request: () => toggleFollowByAccountApi(postData.author.accountId, next),
      commit: () => {
        syncProfileFollowing();
        message.success(next ? '已关注' : '已取消关注');
      },
      rollback: (err) => {
        setPostState((current) =>
          current ? { ...current, followedAuthor: !next } : current,
        );
        message.error(formatApiError('关注失败', err));
      },
    });
  };

  const handleFavorite = async () => {
    if (!requireLogin()) return;
    const currentPost = postData;
    if (!currentPost) return;
    if (isPending('post-favorite')) return;
    const nextFavorited = !currentPost.stats.favorited;
    const nextFavoriteCount = Math.max(
      0,
      currentPost.stats.favoriteCount + (nextFavorited ? 1 : -1),
    );
    await runOptimisticAction('post-favorite', {
      apply: () => {
        setPostState((current) =>
          current
            ? {
                ...current,
                stats: {
                  ...current.stats,
                  favorited: nextFavorited,
                  favoriteCount: nextFavoriteCount,
                },
              }
            : current,
        );
        updateInteraction(currentPost.id, {
          favorited: nextFavorited,
          favoriteCount: nextFavoriteCount,
          favoritePending: true,
        });
      },
      request: () => toggleFavoriteApi(currentPost.id, nextFavorited),
      commit: (res) => {
        setPostState((current) =>
          current
            ? { ...current, stats: { ...current.stats, ...res.data } }
            : current,
        );
        updateInteraction(currentPost.id, {
          favorited: res.data.favorited,
          favoriteCount: res.data.favoriteCount,
          favoritePending: false,
        });
        invalidateCommunityFeed();
        invalidateProfileDataCaches(user?.accountId, [
          PROFILE_DATA_DOMAIN.FAVORITES,
        ]);
      },
      rollback: (err) => {
        setPostState((current) =>
          current
            ? {
                ...current,
                stats: {
                  ...current.stats,
                  favorited: currentPost.stats.favorited,
                  favoriteCount: currentPost.stats.favoriteCount,
                },
              }
            : current,
        );
        updateInteraction(currentPost.id, {
          favorited: currentPost.stats.favorited,
          favoriteCount: currentPost.stats.favoriteCount,
          favoritePending: false,
        });
        message.error(formatApiError('收藏失败', err));
      },
    });
  };

  const handleBottomComment = async (content: string) => {
    if (!requireLogin() || !user?.accountId) return;
    if (commentSubmitting) return;
    const tempId = `optimistic-post-comment-${++optimisticIdRef.current}`;
    const optimisticComment: PostComment = {
      id: tempId,
      accountId: Number(user.accountId),
      nickname: user.username || '我',
      avatar: user.avatar,
      content: content.trim(),
      likeCount: 0,
      liked: false,
      replyCount: 0,
      createdAt: '发送中…',
      replies: [],
      pending: true,
    };
    await runOptimisticAction('post-comment-create', {
      apply: () => {
        setComments((prev) => [optimisticComment, ...prev]);
        setPostState((current) =>
          current
            ? {
                ...current,
                stats: {
                  ...current.stats,
                  commentCount: current.stats.commentCount + 1,
                },
              }
            : current,
        );
        document
          .getElementById('post-comments')
          ?.scrollIntoView({ behavior: 'smooth', block: 'start' });
      },
      request: () =>
        createCommentApi(postData.id, content.trim(), {
          accountId: Number(user.accountId),
          nickname: user.username || '我',
          avatar: user.avatar,
        }),
      commit: (res) => {
        setComments((prev) =>
          prev.map((comment) => (comment.id === tempId ? res.data : comment)),
        );
        invalidateProfileDataCaches(user?.accountId, [
          PROFILE_DATA_DOMAIN.COMMENTS,
        ]);
        message.success('发布成功');
      },
      rollback: (err) => {
        setComments((prev) => prev.filter((comment) => comment.id !== tempId));
        setPostState((current) =>
          current
            ? {
                ...current,
                stats: {
                  ...current.stats,
                  commentCount: Math.max(0, current.stats.commentCount - 1),
                },
              }
            : current,
        );
        message.error(formatApiError('发布失败', err));
      },
    });
  };

  const handleReport = () => {
    if (isOwner || !requireLogin() || !postData) return;
    openReport('article', postData.id, '举报帖子');
  };

  const ownerMenu = isOwner
    ? buildOwnerMenuItems({
        id: postData.id,
        status: postData.status,
        postType: mapContentPostTypeToNumeric(postData.postType),
        title: postData.title,
      })
    : undefined;

  const guestMoreMenu = !isOwner
    ? [
        {
          key: 'report',
          label: '举报',
          icon: <FlagOutlined />,
          onClick: handleReport,
        },
      ]
    : undefined;

  return (
    <div className={`post-detail${isVideo ? ' is-video' : ''}`}>
      <div className="post-detail__top-dock">
        <div className="post-detail__align-track">
          <PostDetailTopBar
            author={postData.author}
            avatarFrameUrl={authorFrameUrl}
            createdAt={postData.createdAt}
            followed={Boolean(postData.followedAuthor)}
            followDisabled={isPending('post-follow')}
            isOwner={isOwner}
            moreMenu={isOwner ? ownerMenu : guestMoreMenu}
            onBack={handleBack}
            onFollow={handleFollow}
            onShare={() => setShareOpen(true)}
          />
        </div>
      </div>

      <div className="post-detail__shell">
        <FeedPanel
          className="post-detail__panel"
          refreshing={refreshing}
          onRefresh={handleRefresh}
        >
          <SurfaceCard className="post-detail__main">
            <PostBody
              post={postData}
              muted
              targetDanmakuId={targetDanmakuId}
              onReportDanmaku={handleReportDanmaku}
              danmakuReportResetKey={danmakuReportResetKey}
            />

            <PostActionBar
              viewCount={postData.stats.viewCount}
              likeCount={postData.stats.likeCount}
              favoriteCount={postData.stats.favoriteCount}
              liked={postData.stats.liked}
              favorited={postData.stats.favorited}
              likeDisabled={interaction.likePending}
              favoriteDisabled={interaction.favoritePending}
              onLike={handleLike}
              onFavorite={handleFavorite}
            />
          </SurfaceCard>

          <SurfaceCard flush className="post-detail__comments">
            <CommentSection
              articleId={postData.id}
              comments={comments}
              onChange={setComments}
              onCommentCountChange={(delta) => {
                setPostState((current) =>
                  current
                    ? {
                        ...current,
                        stats: {
                          ...current.stats,
                          commentCount: Math.max(
                            0,
                            current.stats.commentCount + delta,
                          ),
                        },
                      }
                    : current,
                );
              }}
              loading={commentPager.loading}
              hideComposer
              highlight={commentHighlight}
              infinite={{
                sentinelRef: commentPager.sentinelRef,
                loadingMore: commentPager.loadingMore,
                hasMore: commentPager.hasMore,
                totalCount: postData.stats.commentCount,
              }}
            />
          </SurfaceCard>
        </FeedPanel>

        <div className="post-detail__bottom-dock">
          <div className="post-detail__align-track">
            <PostBottomBar
              className="post-detail__bottom-bar"
              likeCount={postData.stats.likeCount}
              favoriteCount={postData.stats.favoriteCount}
              shareCount={postData.stats.shareCount}
              liked={postData.stats.liked}
              favorited={postData.stats.favorited}
              likeDisabled={interaction.likePending}
              favoriteDisabled={interaction.favoritePending}
              submitting={commentSubmitting}
              onLike={handleLike}
              onFavorite={handleFavorite}
              onShare={() => setShareOpen(true)}
              onComment={handleBottomComment}
            />
          </div>
        </div>
      </div>

      <ShareSheet
        open={shareOpen}
        articleId={postData.id}
        articleTitle={postData.title}
        articleSummary={postData.content}
        coverUrl={postData.coverUrl || postData.images?.[0]}
        videoUrl={postData.videoUrl}
        postType={postData.postType}
        author={postData.author}
        viewCount={postData.stats.viewCount}
        commentCount={postData.stats.commentCount}
        likeCount={postData.stats.likeCount}
        onClose={() => setShareOpen(false)}
        onShared={(shareCount) => {
          if (shareCount > 0) {
            setPostState((p) =>
              p ? { ...p, stats: { ...p.stats, shareCount } } : p,
            );
          }
        }}
        onReposted={(newId) => {
          message.success('已发布转发动态');
          navigate(`/post/${newId}`);
        }}
      />

      {reportTarget ? (
        <ReportModal
          open={reportOpen}
          targetType={reportTarget.type}
          targetId={reportTarget.id}
          title={reportTarget.title}
          onClose={handleReportClose}
        />
      ) : null}
    </div>
  );
}

export default PostDetail;
