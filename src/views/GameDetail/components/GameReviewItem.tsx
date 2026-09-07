import React, { useCallback, useEffect, useMemo, useState } from 'react';
import type { FC } from 'react';
import { App, Rate } from 'antd';

import CommentItem from '@/components/CommentItem';
import { REPLY_PAGE_SIZE } from '@/components/CommentItem/config';
import ReplyPopup from '@/components/ReplyPopup';
import { useAppSelector } from '@/store';
import { useUserDecorations } from '@/hooks/useUserDecorations';
import { formatApiError } from '@/utils/apiError';
import { formatDateTime } from '@/utils/mapPost';
import type { PostComment, PostReply } from '@/types/post';
import type { IGameReview, IGameReviewReply } from '@/types/game';
import {
  addGameReviewReplyApi,
  deleteGameReviewReplyApi,
  fetchGameReviewRepliesApi,
  likeGameReviewApi,
  likeGameReviewReplyApi,
  unlikeGameReviewApi,
  unlikeGameReviewReplyApi,
} from '@/service/game';

import './GameReviewItem.less';

const REVIEW_REPLY_PAGE_SIZE = REPLY_PAGE_SIZE;

interface GameReviewItemProps {
  review: IGameReview;
  onRequireLogin: () => boolean;
  onChanged?: () => void;
  highlighted?: boolean;
  highlightReplyId?: string;
}

function mapReply(reply: IGameReviewReply): PostReply {
  return {
    id: reply.replyId,
    accountId: reply.accountId,
    nickname: reply.username || `玩家${reply.accountId}`,
    avatar: reply.avatar,
    replyToAccountId: reply.replyToAccountId,
    replyToNickname: reply.replyToNickname,
    content: reply.content,
    likeCount: reply.likeCount ?? 0,
    liked: Boolean(reply.liked),
    createdAt: formatDateTime(reply.createTime),
  };
}

const GameReviewItem: FC<GameReviewItemProps> = ({
  review,
  onRequireLogin,
  onChanged,
  highlighted = false,
  highlightReplyId,
}) => {
  const { message } = App.useApp();
  const myAccountId = useAppSelector((state) => state.auth.user?.accountId);
  const [liked, setLiked] = useState(Boolean(review.liked));
  const [likeCount, setLikeCount] = useState(review.likeCount ?? 0);
  const [replies, setReplies] = useState<PostReply[]>([]);
  const [replyCount, setReplyCount] = useState(review.replyCount ?? 0);
  const [repliesLoaded, setRepliesLoaded] = useState(false);
  const [repliesLoading, setRepliesLoading] = useState(false);
  const [repliesPage, setRepliesPage] = useState(0);
  const [expanded, setExpanded] = useState(false);
  const [replyTarget, setReplyTarget] = useState<{
    accountId: number;
    nickname: string;
    afterReplyId?: string;
  } | null>(null);
  const [replySubmitting, setReplySubmitting] = useState(false);

  useEffect(() => {
    setLiked(Boolean(review.liked));
    setLikeCount(review.likeCount ?? 0);
    setReplyCount(review.replyCount ?? 0);
  }, [review.likeCount, review.liked, review.replyCount]);

  const decorationUserIds = useMemo(
    () => [review.accountId, ...replies.map((reply) => reply.accountId)],
    [replies, review.accountId],
  );
  const { get: getDecoration } = useUserDecorations(decorationUserIds);

  const comment = useMemo<PostComment>(
    () => ({
      id: review.reviewId,
      accountId: review.accountId,
      nickname: review.username || `玩家${review.accountId}`,
      avatar: review.avatar,
      content: review.content || '这位玩家没有留下文字评价。',
      likeCount,
      liked,
      replyCount,
      createdAt: formatDateTime(review.createTime),
      replies,
      replyPage: repliesLoaded ? repliesPage : 0,
      replyPageSize: REVIEW_REPLY_PAGE_SIZE,
    }),
    [likeCount, liked, replies, replyCount, repliesLoaded, repliesPage, review],
  );

  // 评价首屏预加载第 1 页回复；因此第一次展开只是展开已加载内容。
  useEffect(() => {
    let cancelled = false;
    setReplies([]);
    setRepliesPage(0);
    setRepliesLoaded(false);
    setExpanded(false);
    setRepliesLoading(true);
    void fetchGameReviewRepliesApi(review.reviewId, 1, REVIEW_REPLY_PAGE_SIZE)
      .then((res) => {
        if (cancelled) return;
        setReplies((res.data || []).map(mapReply));
        setReplyCount(Number(res.total ?? review.replyCount ?? 0));
        setRepliesPage(Number(res.page ?? 1));
        setRepliesLoaded(true);
      })
      .catch((err) => {
        if (!cancelled) {
          message.error(formatApiError('加载回复失败', err));
        }
      })
      .finally(() => {
        if (!cancelled) setRepliesLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [message, review.replyCount, review.reviewId]);

  const loadReplies = useCallback(
    async (mode: 'toggle' | 'more' = 'toggle') => {
      if (repliesLoading) return;
      if (mode === 'toggle' && repliesLoaded) {
        setExpanded((value) => !value);
        return;
      }
      const nextPage = mode === 'more' ? repliesPage + 1 : 1;
      if (
        mode === 'more' &&
        repliesPage * REVIEW_REPLY_PAGE_SIZE >= replyCount
      ) {
        return;
      }
      setExpanded(true);
      setRepliesLoading(true);
      try {
        const res = await fetchGameReviewRepliesApi(
          review.reviewId,
          nextPage,
          REVIEW_REPLY_PAGE_SIZE,
        );
        const nextReplies = (res.data || []).map(mapReply);
        setReplies((current) =>
          mode === 'more'
            ? [
                ...current,
                ...nextReplies.filter(
                  (reply) => !current.some((item) => item.id === reply.id),
                ),
              ]
            : nextReplies,
        );
        const serverTotal = Number(res.total ?? replyCount);
        const uniqueNextReplies = nextReplies.filter(
          (reply) => !replies.some((item) => item.id === reply.id),
        );
        const mergedCount =
          mode === 'more'
            ? replies.length + uniqueNextReplies.length
            : nextReplies.length;
        const hasMore =
          nextReplies.length >= REVIEW_REPLY_PAGE_SIZE &&
          nextPage * REVIEW_REPLY_PAGE_SIZE < serverTotal;
        setReplyCount(
          hasMore ? Math.max(serverTotal, mergedCount) : mergedCount,
        );
        setRepliesPage(Number(res.page ?? nextPage));
        setRepliesLoaded(true);
      } catch (err) {
        message.error(formatApiError('加载回复失败', err));
      } finally {
        setRepliesLoading(false);
      }
    },
    [
      message,
      repliesLoaded,
      repliesLoading,
      repliesPage,
      replyCount,
      replies,
      review.reviewId,
    ],
  );

  useEffect(() => {
    if (!highlighted) return;
    if (
      highlightReplyId &&
      !replies.some((reply) => reply.id === highlightReplyId)
    ) {
      if (!repliesLoaded) {
        void loadReplies();
      } else if (replies.length < replyCount) {
        void loadReplies('more');
      }
      return;
    }
    window.setTimeout(() => {
      const targetId = highlightReplyId
        ? `reply-${highlightReplyId}`
        : `comment-${review.reviewId}`;
      document
        .getElementById(targetId)
        ?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }, 120);
  }, [
    highlightReplyId,
    highlighted,
    loadReplies,
    replies,
    replyCount,
    repliesLoaded,
    review.reviewId,
  ]);

  const toggleLike = async () => {
    if (!onRequireLogin()) return;
    const next = !liked;
    try {
      if (next) await likeGameReviewApi(review.reviewId);
      else await unlikeGameReviewApi(review.reviewId);
      setLiked(next);
      setLikeCount((value) => Math.max(0, value + (next ? 1 : -1)));
      onChanged?.();
    } catch (err) {
      message.error(formatApiError('更新点赞失败', err));
    }
  };

  const toggleReplyLike = async (replyId: string) => {
    if (!onRequireLogin()) return;
    const target = replies.find((reply) => reply.id === replyId);
    if (!target) return;
    const next = !target.liked;
    try {
      if (next) await likeGameReviewReplyApi(replyId);
      else await unlikeGameReviewReplyApi(replyId);
      setReplies((items) =>
        items.map((reply) =>
          reply.id === replyId
            ? {
                ...reply,
                liked: next,
                likeCount: Math.max(0, reply.likeCount + (next ? 1 : -1)),
              }
            : reply,
        ),
      );
      onChanged?.();
    } catch (err) {
      message.error(formatApiError('更新回复点赞失败', err));
    }
  };

  const submitReply = async (content: string) => {
    if (!onRequireLogin() || !replyTarget || replySubmitting) return;
    setReplySubmitting(true);
    try {
      await addGameReviewReplyApi(review.reviewId, content, {
        replyToReplyId: replyTarget.afterReplyId,
      });
      const res = await fetchGameReviewRepliesApi(
        review.reviewId,
        1,
        REVIEW_REPLY_PAGE_SIZE,
      );
      setReplies((res.data || []).map(mapReply));
      setReplyCount(Number(res.total ?? replyCount + 1));
      setRepliesPage(Number(res.page ?? 1));
      setRepliesLoaded(true);
      setExpanded(true);
      setReplyTarget(null);
      message.success('回复成功');
      onChanged?.();
    } catch (err) {
      message.error(formatApiError('回复失败', err));
    } finally {
      setReplySubmitting(false);
    }
  };

  return (
    <>
      <CommentItem
        comment={comment}
        myAccountId={myAccountId}
        expanded={expanded}
        onToggleExpand={() => void loadReplies()}
        onLoadMoreReplies={() => void loadReplies('more')}
        replyLoading={repliesLoading}
        onLike={() => void toggleLike()}
        onReply={() => {
          if (!onRequireLogin()) return;
          setReplyTarget({
            accountId: review.accountId,
            nickname: review.username || `玩家${review.accountId}`,
          });
        }}
        onReplyLike={toggleReplyLike}
        onReplyToReply={(reply) => {
          if (!onRequireLogin()) return;
          setReplyTarget({
            accountId: reply.accountId,
            nickname: reply.nickname,
            afterReplyId: reply.id,
          });
        }}
        onReplyDelete={async (reply) => {
          try {
            await deleteGameReviewReplyApi(reply.id);
            setReplies((items) => items.filter((item) => item.id !== reply.id));
            setReplyCount((value) => Math.max(0, value - 1));
            onChanged?.();
          } catch (err) {
            message.error(formatApiError('删除回复失败', err));
          }
        }}
        decoration={getDecoration(review.accountId)}
        getReplyDecoration={getDecoration}
        metaExtra={
          <Rate
            count={10}
            disabled
            value={review.score}
            className="game-detail__review-score"
          />
        }
      />

      <ReplyPopup
        open={Boolean(replyTarget)}
        nickname={replyTarget?.nickname || ''}
        loading={replySubmitting}
        onClose={() => setReplyTarget(null)}
        onSubmit={submitReply}
      />
    </>
  );
};

export default GameReviewItem;
