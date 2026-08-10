import { useCallback } from 'react';
import { Modal, message } from 'antd';
import type { MenuProps } from 'antd';
import { useNavigate } from 'react-router-dom';

import type { PostOwnerLinkItem } from '@/components/PostOwnerLinks';
import type { PostRowActionItem } from '@/base-ui/PostRowActionBar';
import {
  ARTICLE_STATUS,
  deleteArticleApi,
  submitArticleAuditApi,
  unpublishArticleApi,
} from '@/service/content';
import { startArticleProgressTrack } from '@/store/modules/articleProgress';
import { useAppDispatch } from '@/store';
import {
  canEditArticle,
  getPublishToggleLabel,
  isPublishAction,
} from '@/utils/articleOwnerOps';
import { getArticleProgressResultMessage } from '@/utils/articleProgressMessage';
import { formatApiError } from '@/utils/apiError';

interface UseArticleOwnerActionsOptions {
  onPublished?: () => void;
  onUnpublished?: () => void;
  onDeleted?: () => void;
}

interface OwnerActionParams {
  id: string;
  status?: number;
  postType?: number;
  title?: string;
  onShare?: () => void;
}

export function useArticleOwnerActions(
  options?: UseArticleOwnerActionsOptions,
) {
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const onPublished = options?.onPublished;
  const onUnpublished = options?.onUnpublished;
  const onDeleted = options?.onDeleted;

  const goEdit = useCallback(
    (id: string) => {
      navigate(`/post/editor?id=${id}`);
    },
    [navigate],
  );

  const publish = useCallback(
    (id: string, title?: string) => {
      const displayTitle = title?.trim() || '内容';
      Modal.confirm({
        title: '提交上架？',
        content: '将提交审核，审核通过后对其他人可见。',
        okText: '上架',
        cancelText: '再想想',
        onOk: async () => {
          try {
            await submitArticleAuditApi(id);
            dispatch(
              startArticleProgressTrack({
                articleId: id,
                title: displayTitle,
                kind: 'audit',
              }),
            );
            onPublished?.();
          } catch (err) {
            message.error({
              content: formatApiError('上架失败', err),
              duration: 1,
            });
            throw err;
          }
        },
      });
    },
    [dispatch, onPublished],
  );

  const unpublish = useCallback(
    (id: string, status?: number, title?: string) => {
      const isPublished = status === ARTICLE_STATUS.PUBLISHED;
      const displayTitle = title?.trim() || '内容';
      Modal.confirm({
        title: isPublished ? '下架内容？' : '取消上架？',
        content: isPublished
          ? '下架后内容将移入草稿，其他用户将无法看到。'
          : '停止上传、取消审核任务，保留已上传文件。',
        okText: isPublished ? '下架' : '取消上架',
        okButtonProps: { danger: true },
        cancelText: '再想想',
        onOk: async () => {
          try {
            await unpublishArticleApi(id);
            const { type, text } = getArticleProgressResultMessage(
              isPublished ? ARTICLE_STATUS.DRAFT : ARTICLE_STATUS.OFFLINE,
              displayTitle,
              'unpublish',
            );
            message.open({ type, content: text, duration: 1 });
            onUnpublished?.();
          } catch (err) {
            message.error({
              content: formatApiError('操作失败', err),
              duration: 1,
            });
            throw err;
          }
        },
      });
    },
    [onUnpublished],
  );

  const remove = useCallback(
    (id: string, redirectTo?: string) => {
      Modal.confirm({
        title: '删除内容？',
        content: '将删除文件与元数据，不可恢复。',
        okText: '删除',
        okButtonProps: { danger: true },
        cancelText: '再想想',
        onOk: async () => {
          try {
            await deleteArticleApi(id);
            message.success({ content: '已删除', duration: 1 });
            onDeleted?.();
            if (redirectTo) navigate(redirectTo, { replace: true });
          } catch (err) {
            message.error({
              content: formatApiError('删除失败', err),
              duration: 1,
            });
            throw err;
          }
        },
      });
    },
    [navigate, onDeleted],
  );

  const togglePublish = useCallback(
    (id: string, status?: number, title?: string) => {
      if (isPublishAction(status)) {
        publish(id, title);
        return;
      }
      unpublish(id, status, title);
    },
    [publish, unpublish],
  );

  const buildOwnerLinkItems = useCallback(
    ({
      id,
      status,
      postType,
      title,
    }: OwnerActionParams): PostOwnerLinkItem[] => {
      const items: PostOwnerLinkItem[] = [];
      const toggleLabel = getPublishToggleLabel(status);

      if (canEditArticle(postType)) {
        items.push({
          key: 'edit',
          label: '编辑',
          onClick: () => goEdit(id),
        });
      }

      if (toggleLabel) {
        items.push({
          key: 'toggle',
          label: toggleLabel,
          onClick: () => togglePublish(id, status, title),
        });
      }

      items.push({
        key: 'delete',
        label: '删除',
        danger: true,
        onClick: () => remove(id),
      });

      return items;
    },
    [goEdit, remove, togglePublish],
  );

  const buildOwnerActionBarItems = useCallback(
    ({
      id,
      status,
      postType,
      title,
      onShare,
    }: OwnerActionParams): PostRowActionItem[] => {
      const items: PostRowActionItem[] = [];
      const toggleLabel = getPublishToggleLabel(status);

      if (status === ARTICLE_STATUS.PUBLISHED && onShare) {
        items.push({
          key: 'share',
          label: '分享',
          onClick: onShare,
        });
      }

      if (canEditArticle(postType)) {
        items.push({
          key: 'edit',
          label: '编辑',
          onClick: () => goEdit(id),
        });
      }

      if (toggleLabel) {
        items.push({
          key: 'toggle',
          label: toggleLabel,
          onClick: () => togglePublish(id, status, title),
        });
      }

      items.push({
        key: 'delete',
        label: '删除',
        danger: true,
        onClick: () => remove(id),
      });

      return items;
    },
    [goEdit, remove, togglePublish],
  );

  const buildOwnerMenuItems = useCallback(
    ({ id, status, postType, title }: OwnerActionParams): MenuProps['items'] =>
      buildOwnerLinkItems({ id, status, postType, title }).map((item) => ({
        key: item.key,
        label: item.label,
        danger: item.danger,
        onClick: item.onClick,
      })),
    [buildOwnerLinkItems],
  );

  return {
    goEdit,
    publish,
    unpublish,
    remove,
    togglePublish,
    buildOwnerLinkItems,
    buildOwnerActionBarItems,
    buildOwnerMenuItems,
  };
}
