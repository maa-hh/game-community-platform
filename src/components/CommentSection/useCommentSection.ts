import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { message } from 'antd';

import { useReportModal } from '@/hooks/useReportModal';
import type { PostComment, PostReply } from '@/types/post';
import {
  createCommentApi,
  createReplyApi,
  fetchPostRepliesPageApi,
  toggleCommentLikeApi,
  toggleReplyLikeApi,
} from '@/service/social';
import { useRequireLogin } from '@/hooks/useRequireLogin';
import { formatApiError } from '@/utils/apiError';

import type { ICommentSectionProps, ReplyTarget } from './types';

export function useCommentSection({
  articleId,
  comments,
  onChange,
  highlight,
}: ICommentSectionProps) {
  const { user, requireLogin, openAuth } = useRequireLogin();
  const { reportOpen, reportTarget, openReport, closeReport } =
    useReportModal();
  const [draft, setDraft] = useState('');
  const [replyTarget, setReplyTarget] = useState<ReplyTarget | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [expandedReplyIds, setExpandedReplyIds] = useState<
    Record<string, boolean>
  >({});
  const [replyLoadingIds, setReplyLoadingIds] = useState<
    Record<string, boolean>
  >({});
  const highlightAppliedRef = useRef('');

  const mutateComments = useCallback(
    (updater: (prev: PostComment[]) => PostComment[]) => {
      onChange((prev) => updater(prev));
    },
    [onChange],
  );

  const me = useMemo(
    () =>
      user?.accountId
        ? {
            accountId: Number(user.accountId),
            nickname: user.username || '我',
            avatar: user.avatar,
          }
        : null,
    [user],
  );

  const patchComment = (commentId: string, patch: Partial<PostComment>) => {
    mutateComments((prev) =>
      prev.map((c) => (c.id === commentId ? { ...c, ...patch } : c)),
    );
  };

  const handleCommentLike = async (commentId: string) => {
    if (!requireLogin()) return;

    const current = comments.find((c) => c.id === commentId);
    if (!current) return;

    const snapshot = { liked: current.liked, likeCount: current.likeCount };
    const next = !current.liked;

    mutateComments((prev) =>
      prev.map((c) =>
        c.id === commentId
          ? {
              ...c,
              liked: next,
              likeCount: Math.max(0, c.likeCount + (next ? 1 : -1)),
            }
          : c,
      ),
    );

    try {
      const res = await toggleCommentLikeApi(articleId, commentId, next);
      mutateComments((prev) =>
        prev.map((c) =>
          c.id === commentId
            ? { ...c, liked: res.data.liked, likeCount: res.data.likeCount }
            : c,
        ),
      );
    } catch (err) {
      mutateComments((prev) =>
        prev.map((c) => (c.id === commentId ? { ...c, ...snapshot } : c)),
      );
      message.error(formatApiError('操作失败', err));
    }
  };

  const handleReplyLike = async (commentId: string, replyId: string) => {
    if (!requireLogin()) return;

    const comment = comments.find((c) => c.id === commentId);
    const reply = comment?.replies.find((r) => r.id === replyId);
    if (!reply) return;

    const snapshot = { liked: reply.liked, likeCount: reply.likeCount };
    const next = !reply.liked;

    mutateComments((prev) =>
      prev.map((c) => {
        if (c.id !== commentId) return c;
        return {
          ...c,
          replies: c.replies.map((r) =>
            r.id === replyId
              ? {
                  ...r,
                  liked: next,
                  likeCount: Math.max(0, r.likeCount + (next ? 1 : -1)),
                }
              : r,
          ),
        };
      }),
    );

    try {
      const res = await toggleReplyLikeApi(articleId, commentId, replyId, next);
      mutateComments((prev) =>
        prev.map((c) => {
          if (c.id !== commentId) return c;
          return {
            ...c,
            replies: c.replies.map((r) =>
              r.id === replyId
                ? {
                    ...r,
                    liked: res.data.liked,
                    likeCount: res.data.likeCount,
                  }
                : r,
            ),
          };
        }),
      );
    } catch (err) {
      mutateComments((prev) =>
        prev.map((c) => {
          if (c.id !== commentId) return c;
          return {
            ...c,
            replies: c.replies.map((r) =>
              r.id === replyId ? { ...r, ...snapshot } : r,
            ),
          };
        }),
      );
      message.error(formatApiError('操作失败', err));
    }
  };

  const insertReplyLocal = (
    commentId: string,
    reply: PostReply,
    afterReplyId?: string,
  ) => {
    mutateComments((prev) => {
      return prev.map((c) => {
        if (c.id !== commentId) return c;
        const next = [...c.replies];
        if (afterReplyId) {
          const idx = next.findIndex((r) => r.id === afterReplyId);
          if (idx >= 0) next.splice(idx + 1, 0, reply);
          else next.unshift(reply);
        } else {
          next.unshift(reply);
        }
        return {
          ...c,
          replyCount: c.replyCount + 1,
          replies: next,
        };
      });
    });
    setExpandedReplyIds((prev) => ({ ...prev, [commentId]: true }));
  };

  const submitComment = async () => {
    if (!requireLogin() || !me) return;
    const content = draft.trim();
    if (!content) {
      message.warning('请输入内容');
      return;
    }
    setSubmitting(true);
    try {
      const res = await createCommentApi(articleId, content, me);
      mutateComments((prev) => [res.data, ...prev]);
      setDraft('');
      message.success('发布成功');
    } catch (err) {
      message.error(formatApiError('发布失败', err));
    } finally {
      setSubmitting(false);
    }
  };

  const submitReply = async (content: string) => {
    if (!requireLogin() || !me || !replyTarget) return;
    setSubmitting(true);
    try {
      const res = await createReplyApi(
        articleId,
        replyTarget.commentId,
        content,
        {
          accountId: replyTarget.accountId,
          nickname: replyTarget.nickname,
        },
        me,
        replyTarget.afterReplyId,
      );
      insertReplyLocal(
        replyTarget.commentId,
        res.data,
        replyTarget.afterReplyId,
      );
      setReplyTarget(null);
      message.success('回复成功');
    } catch (err) {
      message.error(formatApiError('回复失败', err));
    } finally {
      setSubmitting(false);
    }
  };

  const onReport = (targetType: 'comment' | 'reply', targetId: string) => {
    if (!requireLogin()) return;

    const isMine =
      targetType === 'comment'
        ? comments.some(
            (comment) =>
              comment.id === targetId && comment.accountId === me?.accountId,
          )
        : comments.some((comment) =>
            comment.replies.some(
              (reply) =>
                reply.id === targetId && reply.accountId === me?.accountId,
            ),
          );
    if (isMine) return;

    openReport(targetType, targetId);
  };

  const openReply = (target: ReplyTarget) => {
    if (!requireLogin()) return;
    setReplyTarget(target);
  };

  const loadReplies = useCallback(
    async (commentId: string, mode: 'expand' | 'more') => {
      if (replyLoadingIds[commentId]) return;
      const comment = comments.find((c) => c.id === commentId);
      if (!comment) return;

      setReplyLoadingIds((prev) => ({ ...prev, [commentId]: true }));
      try {
        const pageSize = 20;
        const isExpand = mode === 'expand';
        const requestPage = isExpand
          ? 1
          : Math.floor(comment.replies.length / pageSize) + 1;
        const requestSize = isExpand
          ? Math.min(Math.max(comment.replyCount, 1), 100)
          : pageSize;

        const res = await fetchPostRepliesPageApi(
          commentId,
          requestPage,
          requestSize,
        );
        const batch = res.data || [];
        const serverTotal = Number(res.total ?? comment.replyCount);
        const fetchedAll = batch.length < requestSize;

        mutateComments((prev) =>
          prev.map((c) => {
            if (c.id !== commentId) return c;
            const merged = isExpand ? batch : [...c.replies, ...batch];
            const seen = new Set<string>();
            const replies = merged.filter((reply) => {
              if (seen.has(reply.id)) return false;
              seen.add(reply.id);
              return true;
            });
            const replyCount = fetchedAll
              ? replies.length
              : Math.max(serverTotal, replies.length);
            return {
              ...c,
              replies,
              replyCount,
            };
          }),
        );
      } catch (err) {
        message.error(formatApiError('加载回复失败', err));
      } finally {
        setReplyLoadingIds((prev) => {
          const next = { ...prev };
          delete next[commentId];
          return next;
        });
      }
    },
    [comments, mutateComments, replyLoadingIds],
  );

  const toggleExpand = async (commentId: string) => {
    const comment = comments.find((c) => c.id === commentId);
    const nextExpanded = !expandedReplyIds[commentId];
    setExpandedReplyIds((prev) => ({
      ...prev,
      [commentId]: nextExpanded,
    }));
    if (
      nextExpanded &&
      comment &&
      comment.replies.length < comment.replyCount
    ) {
      await loadReplies(commentId, 'expand');
    }
  };

  const loadMoreReplies = async (commentId: string) => {
    const comment = comments.find((c) => c.id === commentId);
    if (!comment || comment.replies.length >= comment.replyCount) return;
    await loadReplies(commentId, 'more');
  };

  useEffect(() => {
    const commentId = highlight?.commentId;
    if (!commentId || comments.length === 0) return;
    const token = `${commentId}:${highlight?.replyId || ''}`;
    if (highlightAppliedRef.current === token) return;

    const run = async () => {
      const comment = comments.find((c) => c.id === commentId);
      if (!comment) return;
      highlightAppliedRef.current = token;
      setExpandedReplyIds((prev) => ({ ...prev, [commentId]: true }));
      if (comment.replies.length < comment.replyCount) {
        await loadReplies(commentId, 'expand');
      }
      window.setTimeout(() => {
        const targetId = highlight?.replyId
          ? `reply-${highlight.replyId}`
          : `comment-${commentId}`;
        document
          .getElementById(targetId)
          ?.scrollIntoView({ behavior: 'smooth', block: 'center' });
      }, 120);
    };

    void run();
  }, [comments, highlight?.commentId, highlight?.replyId, loadReplies]);

  return {
    draft,
    setDraft,
    replyTarget,
    setReplyTarget,
    submitting,
    expandedReplyIds,
    me,
    submitComment,
    submitReply,
    onReport,
    openReply,
    toggleExpand,
    loadMoreReplies,
    replyLoadingIds,
    patchComment,
    handleCommentLike,
    handleReplyLike,
    requireLogin,
    openAuth,
    reportOpen,
    reportTarget,
    closeReport,
  };
}
