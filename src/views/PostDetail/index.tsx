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
import { usePostLikeAction } from '@/hooks/usePostLikeAction';
import { useReportModal } from '@/hooks/useReportModal';
import { useUserDecorations } from '@/hooks/useUserDecorations';
import PostBody from '@/views/PostDetail/components/PostBody';
import PostDetailTopBar from '@/views/PostDetail/components/PostDetailTopBar';
import type { PostDetailData } from '@/types/post';
import {
  createCommentApi,
  fetchPostDetailApi,
  toggleFavoriteApi,
  toggleFollowByAccountApi,
} from '@/service/social';
import { ARTICLE_STATUS } from '@/service/content';
import { formatApiError } from '@/utils/apiError';
import { resolveAvatarFrameAsset } from '@/constants/avatarFrameCatalog';
import { mapContentPostTypeToNumeric } from '@/utils/postType';
import { canGoBackInApp } from '@/utils/returnNavigation';

import './style.less';

function PostDetail() {
  const { id = '' } = useParams();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const goBack = useGoBack();
  const { user, requireLogin, openAuth } = useRequireLogin();

  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [post, setPost] = useState<PostDetailData | null>(null);
  const commentPager = usePostComments(id);
  const comments = commentPager.items;
  const setComments = commentPager.setItems;
  const [shareOpen, setShareOpen] = useState(false);
  const [danmakuReportResetKey, setDanmakuReportResetKey] = useState(0);
  const [commentSubmitting, setCommentSubmitting] = useState(false);
  const { reportOpen, reportTarget, openReport, closeReport } =
    useReportModal();

  const postRef = useRef<PostDetailData | null>(null);

  const handleLike = usePostLikeAction(
    id,
    (next) => {
      setPost((current) =>
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

  postRef.current = post;

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
    post?.author.accountId,
  ]);
  const authorDecoration = getAuthorDecoration(post?.author.accountId);
  const authorFrameUrl = resolveAvatarFrameAsset(
    authorDecoration?.avatarFrame?.code,
    authorDecoration?.avatarFrame?.assetJson,
  )?.frameUrl;

  const commentHighlight = useMemo(
    () => ({
      commentId: searchParams.get('commentId') || undefined,
      replyId: searchParams.get('replyId') || undefined,
    }),
    [searchParams],
  );

  const targetDanmakuId = useMemo(() => {
    const value = Number(searchParams.get('danmakuId') || '');
    return Number.isSafeInteger(value) && value > 0 ? value : undefined;
  }, [searchParams]);

  const reloadDetail = useCallback(async () => {
    const detailRes = await fetchPostDetailApi(id);
    if (!detailRes.data) {
      setPost(null);
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
      setPost(null);
      setLoadError('帖子已下架或暂不可见');
      return false;
    }

    setLoadError(null);
    setPost(detail);
    return true;
  }, [id, user?.accountId]);

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
    (async () => {
      setLoading(true);
      try {
        await reloadDetail();
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [reloadDetail]);

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

  if (loadError || !post) {
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

  const isVideo = post.postType === 'video';
  const isOwner =
    user?.accountId != null && Number(user.accountId) === post.author.accountId;

  const handleFollow = async () => {
    if (!requireLogin() || !post) return;
    try {
      const next = !post.followedAuthor;
      await toggleFollowByAccountApi(post.author.accountId, next);
      setPost({ ...post, followedAuthor: next });
      message.success(next ? '已关注' : '已取消关注');
    } catch (err) {
      message.error(formatApiError('关注失败', err));
    }
  };

  const handleFavorite = async () => {
    if (!requireLogin()) return;
    const currentPost = post;
    if (!currentPost) return;
    try {
      const next = !currentPost.stats.favorited;
      const res = await toggleFavoriteApi(currentPost.id, next);
      setPost((current) =>
        current
          ? { ...current, stats: { ...current.stats, ...res.data } }
          : current,
      );
    } catch (err) {
      message.error(formatApiError('收藏失败', err));
    }
  };

  const handleBottomComment = async (content: string) => {
    if (!requireLogin() || !user?.accountId) return;
    setCommentSubmitting(true);
    try {
      const res = await createCommentApi(post.id, content, {
        accountId: Number(user.accountId),
        nickname: user.username || '我',
        avatar: user.avatar,
      });
      setComments((prev) => [res.data, ...prev]);
      setPost((p) =>
        p
          ? {
              ...p,
              stats: {
                ...p.stats,
                commentCount: p.stats.commentCount + 1,
              },
            }
          : p,
      );
      message.success('发布成功');
      document
        .getElementById('post-comments')
        ?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    } catch (err) {
      message.error(formatApiError('发布失败', err));
    } finally {
      setCommentSubmitting(false);
    }
  };

  const handleReport = () => {
    if (isOwner || !requireLogin() || !post) return;
    openReport('article', post.id, '举报帖子');
  };

  const ownerMenu = isOwner
    ? buildOwnerMenuItems({
        id: post.id,
        status: post.status,
        postType: mapContentPostTypeToNumeric(post.postType),
        title: post.title,
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
            author={post.author}
            avatarFrameUrl={authorFrameUrl}
            createdAt={post.createdAt}
            followed={Boolean(post.followedAuthor)}
            isOwner={isOwner}
            moreMenu={isOwner ? ownerMenu : guestMoreMenu}
            onBack={handleBack}
            onFollow={handleFollow}
            onShare={() => setShareOpen(true)}
          />
        </div>
      </div>

      <div className="post-detail__shell">
        <FeedPanel className="post-detail__panel" onRefresh={handleRefresh}>
          <SurfaceCard className="post-detail__main">
            <PostBody
              post={post}
              muted
              targetDanmakuId={targetDanmakuId}
              onReportDanmaku={handleReportDanmaku}
              danmakuReportResetKey={danmakuReportResetKey}
            />

            <PostActionBar
              viewCount={post.stats.viewCount}
              likeCount={post.stats.likeCount}
              favoriteCount={post.stats.favoriteCount}
              liked={post.stats.liked}
              favorited={post.stats.favorited}
              onLike={handleLike}
              onFavorite={handleFavorite}
            />
          </SurfaceCard>

          <SurfaceCard flush className="post-detail__comments">
            <CommentSection
              articleId={post.id}
              comments={comments}
              onChange={setComments}
              hideComposer
              highlight={commentHighlight}
              infinite={{
                sentinelRef: commentPager.sentinelRef,
                loadingMore: commentPager.loadingMore,
                hasMore: commentPager.hasMore,
                totalCount: post.stats.commentCount,
              }}
            />
          </SurfaceCard>
        </FeedPanel>

        <div className="post-detail__bottom-dock">
          <div className="post-detail__align-track">
            <PostBottomBar
              className="post-detail__bottom-bar"
              likeCount={post.stats.likeCount}
              favoriteCount={post.stats.favoriteCount}
              shareCount={post.stats.shareCount}
              liked={post.stats.liked}
              favorited={post.stats.favorited}
              submitting={commentSubmitting}
              onLike={handleLike}
              onFavorite={handleFavorite}
              onShare={() => setShareOpen(true)}
              onComment={handleBottomComment}
              onFocusComment={() => {
                if (!user?.accountId) openAuth('login');
              }}
            />
          </div>
        </div>
      </div>

      <ShareSheet
        open={shareOpen}
        articleId={post.id}
        articleTitle={post.title}
        articleSummary={post.content}
        coverUrl={post.coverUrl || post.images?.[0]}
        videoUrl={post.videoUrl}
        postType={post.postType}
        author={post.author}
        viewCount={post.stats.viewCount}
        commentCount={post.stats.commentCount}
        likeCount={post.stats.likeCount}
        onClose={() => setShareOpen(false)}
        onShared={(shareCount) => {
          if (shareCount > 0) {
            setPost((p) =>
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
