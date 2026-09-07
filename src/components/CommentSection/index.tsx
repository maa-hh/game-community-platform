import React, { memo } from 'react';
import type { FC } from 'react';

import ReplyPopup from '@/components/ReplyPopup';
import ReportModal from '@/components/ReportModal';

import CommentComposer from './parts/CommentComposer';
import CommentList from './parts/CommentList';
import type { ICommentSectionProps } from './types';
import { useCommentSection } from './useCommentSection';

import './style.less';

const CommentSection: FC<ICommentSectionProps> = (props) => {
  const {
    comments,
    onChange,
    hideComposer = false,
    loading = false,
    infinite,
  } = props;
  const {
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
    reportOpen,
    reportTarget,
    closeReport,
  } = useCommentSection(props);

  const totalCount = infinite?.totalCount ?? comments.length;

  return (
    <section className="comment-section" id="post-comments">
      <h2 className="comment-section__title">评论 {totalCount}</h2>

      {!hideComposer && (
        <CommentComposer
          draft={draft}
          submitting={submitting}
          onDraftChange={setDraft}
          onSubmit={submitComment}
        />
      )}

      <CommentList
        articleId={props.articleId}
        comments={comments}
        myAccountId={me?.accountId}
        expandedReplyIds={expandedReplyIds}
        onToggleExpand={toggleExpand}
        onLoadMoreReplies={loadMoreReplies}
        replyLoadingIds={replyLoadingIds}
        onChange={onChange}
        requireLogin={requireLogin}
        onOpenReply={openReply}
        onReport={onReport}
        patchComment={patchComment}
        onCommentLike={handleCommentLike}
        onReplyLike={handleReplyLike}
        infinite={infinite}
        loading={loading}
      />

      <ReplyPopup
        open={Boolean(replyTarget)}
        nickname={replyTarget?.nickname || ''}
        loading={submitting}
        onClose={() => setReplyTarget(null)}
        onSubmit={submitReply}
      />

      {reportTarget ? (
        <ReportModal
          open={reportOpen}
          targetType={reportTarget.type}
          targetId={reportTarget.id}
          title={reportTarget.title}
          onClose={closeReport}
        />
      ) : null}
    </section>
  );
};

export default memo(CommentSection);
