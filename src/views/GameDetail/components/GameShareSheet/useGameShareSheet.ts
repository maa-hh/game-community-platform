import { useMemo, useState } from 'react';
import { App } from 'antd';

import { createGameSharePostApi } from '@/service/game';
import { useRequireLogin } from '@/hooks/useRequireLogin';
import { formatApiError } from '@/utils/apiError';
import {
  buildDefaultGameShareContent,
  buildDefaultGameShareTitle,
  resolveShareContent,
  resolveShareTitle,
} from '@/utils/shareRepost';
import {
  buildGameDetailPageUrl,
  buildShareGameCopyText,
} from '@/utils/shareUrl';
import { communityFeedCacheKey } from '@/hooks/usePostInteraction';
import { invalidatePageDataCache } from '@/hooks/pageDataCache';
import { invalidateProfileDataCaches } from '@/utils/profileDataCache';
import { PROFILE_DATA_DOMAIN } from '@/types/profileRealtime';

import type { IGameShareSheetProps } from './types';

export function useGameShareSheet({
  detail,
  onClose,
  onReposted,
}: IGameShareSheetProps) {
  const { message } = App.useApp();
  const { requireLogin, user } = useRequireLogin();
  const [repostOpen, setRepostOpen] = useState(false);
  const [shareTitle, setShareTitle] = useState('');
  const [shareContent, setShareContent] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const copyText = async (text: string, successMessage: string) => {
    try {
      await navigator.clipboard.writeText(text);
      message.success(successMessage);
      return true;
    } catch {
      message.error('复制失败，请手动复制');
      return false;
    }
  };

  const defaultShareTitle = useMemo(
    () => buildDefaultGameShareTitle(detail.name),
    [detail.name],
  );
  const defaultShareContent = useMemo(
    () => buildDefaultGameShareContent(detail.name),
    [detail.name],
  );

  const detailUrl = useMemo(
    () => buildGameDetailPageUrl(detail.appId),
    [detail.appId],
  );

  const copyTextPreview = useMemo(
    () =>
      buildShareGameCopyText({
        name: detail.name,
        summary: detail.shortDescription || detail.description,
        url: detailUrl,
      }),
    [detail.description, detail.name, detail.shortDescription, detailUrl],
  );

  const handleCopySteam = async () => {
    if (!detail.steamUrl) return;
    const ok = await copyText(detail.steamUrl, '已复制 Steam 链接');
    if (ok) onClose();
  };

  const handleCopyDetail = async () => {
    const ok = await copyText(detailUrl, '已复制游戏详情链接');
    if (ok) onClose();
  };

  const openRepost = () => {
    if (!requireLogin()) return;
    setRepostOpen(true);
  };

  const submitRepost = async () => {
    setSubmitting(true);
    try {
      const newId = await createGameSharePostApi({
        appId: detail.appId,
        gameName: detail.name,
        gameSummary: detail.shortDescription || detail.description,
        coverUrl: detail.coverUrl,
        title: resolveShareTitle(shareTitle, defaultShareTitle),
        content: resolveShareContent(shareContent, defaultShareContent),
      });
      message.success('已分享为动态');
      invalidatePageDataCache(communityFeedCacheKey(user?.accountId));
      invalidateProfileDataCaches(user?.accountId, [PROFILE_DATA_DOMAIN.POSTS]);
      onReposted?.(newId);
      setShareTitle('');
      setShareContent('');
      setRepostOpen(false);
      onClose();
    } catch (err) {
      message.error(formatApiError('分享失败', err));
    } finally {
      setSubmitting(false);
    }
  };

  return {
    detailUrl,
    copyTextPreview,
    repostOpen,
    shareTitle,
    shareContent,
    submitting,
    setShareTitle,
    setShareContent,
    setRepostOpen,
    handleCopySteam,
    handleCopyDetail,
    openRepost,
    submitRepost,
  };
}
