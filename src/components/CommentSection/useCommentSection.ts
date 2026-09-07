import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { message } from 'antd';

import { useReportModal } from '@/hooks/useReportModal';
import { useOptimisticAction } from '@/hooks/useOptimisticAction';
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
import { REPLY_PAGE_SIZE } from '@/components/CommentItem/config';
import { invalidateProfileDataCaches } from '@/utils/profileDataCache';
import { PROFILE_DATA_DOMAIN } from '@/types/profileRealtime';

import type { ICommentSectionProps, ReplyTarget } from './types';

export function useCommentSection({
  articleId,
  comments,
  onChange,
  onCommentCountChange,
  highlight,
}: ICommentSectionProps) {
  const { user, requireLogin } = useRequireLogin();
  const { reportOpen, reportTarget, openReport, closeReport } =
    useReportModal();
  const [draft, setDraft] = useState('');
  const [replyTarget, setReplyTarget] = useState<ReplyTarget | null>(null);
  const [expandedReplyIds, setExpandedReplyIds] = useState<
    Record<string, boolean>
  >({});
  const [replyLoadingIds, setReplyLoadingIds] = useState<
    Record<string, boolean>
  >({});
  const highlightAppliedRef = useRef('');
  const optimisticIdRef = useRef(0);
  const { run: runOptimisticAction, isPending } = useOptimisticAction();

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

    await runOptimisticAction(`comment-like:${commentId}`, {
      apply: () => {
        mutateComments((prev) =>
          prev.map((c) =>
            c.id === commentId
              ? {
                  ...c,
                  liked: next,
                  likeCount: Math.max(0, c.likeCount + (next ? 1 : -1)),
                  likePending: true,
                }
              : c,
          ),
        );
      },
      request: () => toggleCommentLikeApi(articleId, commentId, next),
      commit: (res) => {
        mutateComments((prev) =>
          prev.map((c) =>
            c.id === commentId
              ? {
                  ...c,
                  liked: res.data.liked,
                  likeCount: res.data.likeCount,
                  likePending: false,
                }
              : c,
          ),
        );
      },
      rollback: (err) => {
        mutateComments((prev) =>
          prev.map((c) =>
            c.id === commentId ? { ...c, ...snapshot, likePending: false } : c,
          ),
        );
        message.error(formatApiError('操作失败', err));
      },
    });
  };

  const handleReplyLike = async (commentId: string, replyId: string) => {
    if (!requireLogin()) return;

    const comment = comments.find((c) => c.id === commentId);
    const reply = comment?.replies.find((r) => r.id === replyId);
    if (!reply) return;

    const snapshot = { liked: reply.liked, likeCount: reply.likeCount };
    const next = !reply.liked;

    await runOptimisticAction(`reply-like:${replyId}`, {
      apply: () => {
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
                      likePending: true,
                    }
                  : r,
              ),
            };
          }),
        );
      },
      request: () =>
        toggleReplyLikeApi(
          articleId,
          commentId,
          replyId,
          next,
          snapshot.likeCount,
        ),
      commit: (res) => {
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
                      likePending: false,
                    }
                  : r,
              ),
            };
          }),
        );
      },
      rollback: (err) => {
        mutateComments((prev) =>
          prev.map((c) => {
            if (c.id !== commentId) return c;
            return {
              ...c,
              replies: c.replies.map((r) =>
                r.id === replyId
                  ? { ...r, ...snapshot, likePending: false }
                  : r,
              ),
            };
          }),
        );
        message.error(formatApiError('操作失败', err));
      },
    });
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
    if (isPending('comment-create') || isPending('reply-create')) return;
    const content = draft.trim();
    if (!content) {
      message.warning('请输入内容');
      return;
    }
    const tempId = `optimistic-comment-${++optimisticIdRef.current}`;
    const optimisticComment: PostComment = {
      id: tempId,
      accountId: me.accountId,
      nickname: me.nickname,
      avatar: me.avatar,
      content,
      likeCount: 0,
      liked: false,
      replyCount: 0,
      createdAt: '发送中…',
      replies: [],
      pending: true,
    };
    setDraft('');
    await runOptimisticAction('comment-create', {
      apply: () => {
        mutateComments((prev) => [optimisticComment, ...prev]);
        onCommentCountChange?.(1);
      },
      request: () => createCommentApi(articleId, content, me),
      commit: (res) => {
        mutateComments((prev) =>
          prev.map((comment) => (comment.id === tempId ? res.data : comment)),
        );
        invalidateProfileDataCaches(me?.accountId, [
          PROFILE_DATA_DOMAIN.COMMENTS,
        ]);
        message.success('发布成功');
      },
      rollback: (err) => {
        mutateComments((prev) =>
          prev.filter((comment) => comment.id !== tempId),
        );
        onCommentCountChange?.(-1);
        message.error(formatApiError('发布失败', err));
      },
    });
  };

  const submitReply = async (content: string) => {
    if (!requireLogin() || !me || !replyTarget) return;
    if (isPending('comment-create') || isPending('reply-create')) return;

    const target = replyTarget;
    const tempId = `optimistic-reply-${++optimisticIdRef.current}`;
    const optimisticReply: PostReply = {
      id: tempId,
      accountId: me.accountId,
      nickname: me.nickname,
      avatar: me.avatar,
      replyToAccountId: target.accountId,
      replyToNickname: target.nickname,
      content,
      likeCount: 0,
      liked: false,
      createdAt: '发送中…',
      pending: true,
    };
    setReplyTarget(null);
    await runOptimisticAction('reply-create', {
      apply: () => {
        insertReplyLocal(
          target.commentId,
          optimisticReply,
          target.afterReplyId,
        );
        onCommentCountChange?.(1);
      },
      request: () =>
        createReplyApi(
          articleId,
          target.commentId,
          content,
          {
            accountId: target.accountId,
            nickname: target.nickname,
          },
          me,
          target.afterReplyId,
        ),
      commit: (res) => {
        mutateComments((prev) =>
          prev.map((comment) =>
            comment.id === target.commentId
              ? {
                  ...comment,
                  replies: comment.replies.map((reply) =>
                    reply.id === tempId ? res.data : reply,
                  ),
                }
              : comment,
          ),
        );
        invalidateProfileDataCaches(me?.accountId, [
          PROFILE_DATA_DOMAIN.COMMENTS,
        ]);
        message.success('回复成功');
      },
      rollback: (err) => {
        mutateComments((prev) =>
          prev.map((comment) =>
            comment.id === target.commentId
              ? {
                  ...comment,
                  replyCount: Math.max(0, comment.replyCount - 1),
                  replies: comment.replies.filter(
                    (reply) => reply.id !== tempId,
                  ),
                }
              : comment,
          ),
        );
        onCommentCountChange?.(-1);
        message.error(formatApiError('回复失败', err));
      },
    });
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
        const pageSize = REPLY_PAGE_SIZE;
        const isExpand = mode === 'expand';
        const requestPage = isExpand ? 1 : (comment.replyPage ?? 0) + 1;

        const res = await fetchPostRepliesPageApi(
          commentId,
          requestPage,
          pageSize,
        );
        const batch = res.data || [];
        const serverTotal = Number(res.total ?? comment.replyCount);
        const fetchedAll = batch.length < pageSize;

        mutateComments((prev) =>
          prev.map((c) => {
            if (c.id !== commentId) return c;
            const pendingReplies = c.replies.filter((reply) => reply.pending);
            const seededReplies =
              isExpand && !c.replyPage
                ? c.replies.filter((reply) => !reply.pending)
                : [];
            const merged = isExpand
              ? [...pendingReplies, ...seededReplies, ...batch]
              : [...c.replies, ...batch];
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
              replyPage: Number(res.page ?? requestPage),
              replyPageSize: pageSize,
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
    if (nextExpanded && comment && !comment.replyPage) {
      await loadReplies(commentId, 'expand');
    }
  };

  const loadMoreReplies = async (commentId: string) => {
    const comment = comments.find((c) => c.id === commentId);
    const pageSize = comment?.replyPageSize ?? REPLY_PAGE_SIZE;
    if (!comment || (comment.replyPage ?? 0) * pageSize >= comment.replyCount) {
      return;
    }
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
      if (!comment.replyPage) {
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
    submitting: isPending('comment-create') || isPending('reply-create'),
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
    reportOpen,
    reportTarget,
    closeReport,
  };
}
