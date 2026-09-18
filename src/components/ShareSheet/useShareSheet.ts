import { useMemo, useState } from 'react';
import { App } from 'antd';

import { createRepostApi, recordShareApi } from '@/service/social';
import { useRequireLogin } from '@/hooks/useRequireLogin';
import {
  buildDefaultRepostContent,
  buildDefaultRepostTitle,
  resolveShareContent,
  resolveShareTitle,
} from '@/utils/shareRepost';
import { buildShareCopyText, buildSharePostUrl } from '@/utils/shareUrl';
import { formatApiError } from '@/utils/apiError';
import { communityFeedCacheKey } from '@/hooks/usePostInteraction';
import { invalidatePageDataCache } from '@/hooks/pageDataCache';
import { invalidateProfileDataCaches } from '@/utils/profileDataCache';
import { PROFILE_DATA_DOMAIN } from '@/types/profileRealtime';

import type { IShareSheetProps } from './types';

export function useShareSheet({
  open: _open,
  articleId,
  articleTitle,
  articleSummary,
  coverUrl,
  videoUrl,
  postType = 'image_text',
  author,
  viewCount = 0,
  commentCount = 0,
  likeCount = 0,
  onClose,
  onShared,
  onReposted,
}: IShareSheetProps) {
  const { message } = App.useApp();
  const { user, requireLogin } = useRequireLogin();
  const [repostOpen, setRepostOpen] = useState(false);
  const [repostTitle, setRepostTitle] = useState('');
  const [repostContent, setRepostContent] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const defaultRepostTitle = useMemo(
    () => buildDefaultRepostTitle(articleTitle),
    [articleTitle],
  );
  const defaultRepostContent = useMemo(
    () => buildDefaultRepostContent(articleTitle),
    [articleTitle],
  );

  const shareUrl = useMemo(() => buildSharePostUrl(articleId), [articleId]);

  const shareCard = useMemo(
    () => ({
      id: articleId,
      title: articleTitle,
      summary: articleSummary,
      coverUrl,
      videoUrl,
      postType,
      author: {
        accountId: author.accountId,
        nickname: author.nickname,
        avatar: author.avatar,
      },
      viewCount,
      commentCount,
      likeCount,
    }),
    [
      articleId,
      articleTitle,
      articleSummary,
      coverUrl,
      videoUrl,
      postType,
      author,
      viewCount,
      commentCount,
      likeCount,
    ],
  );

  const copyText = useMemo(
    () =>
      buildShareCopyText({
        title: articleTitle,
        summary: articleSummary,
        url: shareUrl,
      }),
    [articleTitle, articleSummary, shareUrl],
  );

  const bumpShare = async () => {
    if (!user?.accountId) return;
    try {
      const res = await recordShareApi(articleId);
      invalidatePageDataCache(communityFeedCacheKey(user.accountId));
      onShared?.(res.data.shareCount);
    } catch (err) {
      message.error(formatApiError('分享记录失败', err));
    }
  };

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(copyText);
      message.success('已复制标题、引导语与链接');
      if (user?.accountId) await bumpShare();
      onClose();
    } catch {
      message.error('复制失败，请手动复制');
    }
  };

  const openRepost = () => {
    if (!requireLogin()) return;
    setRepostOpen(true);
  };

  const submitRepost = async () => {
    if (!user?.accountId) return;
    setSubmitting(true);
    try {
      const res = await createRepostApi({
        refArticleId: articleId,
        title: resolveShareTitle(repostTitle, defaultRepostTitle),
        content: resolveShareContent(repostContent, defaultRepostContent),
        user: {
          accountId: Number(user.accountId),
          nickname: user.username || '我',
          avatar: user.avatar,
        },
      });
      message.success('已转发为动态');
      invalidatePageDataCache(communityFeedCacheKey(user.accountId));
      invalidateProfileDataCaches(user.accountId, [PROFILE_DATA_DOMAIN.POSTS]);
      onShared?.(res.data.refShareCount);
      onReposted?.(res.data.post.id);
      setRepostTitle('');
      setRepostContent('');
      setRepostOpen(false);
      onClose();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '转发失败');
    } finally {
      setSubmitting(false);
    }
  };

  return {
    shareCard,
    copyText,
    repostOpen,
    repostTitle,
    repostContent,
    submitting,
    setRepostTitle,
    setRepostContent,
    setRepostOpen,
    handleCopy,
    openRepost,
    submitRepost,
  };
}
