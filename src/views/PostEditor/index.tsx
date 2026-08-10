import React, { useEffect, useRef, useState } from 'react';
import type { FC } from 'react';
import {
  Button,
  Form,
  Input,
  Modal,
  Progress,
  Select,
  Segmented,
  Space,
  Spin,
  Tag,
  Upload,
  message,
} from 'antd';
import { DeleteOutlined, VideoCameraOutlined } from '@ant-design/icons';
import { useNavigate, useSearchParams } from 'react-router-dom';

import VideoPlayer from '@/base-ui/VideoPlayer';
import PageLoading from '@/base-ui/PageLoading';
import {
  ARTICLE_STATUS,
  POST_TYPE,
  bindChunkUploadApi,
  deleteArticleApi,
  listCategoriesApi,
  loadArticleForEditApi,
  saveArticleApi,
  unpublishArticleApi,
  uploadImagesApi,
  uploadVideoWithProgress,
  type ICategory,
  type PostType,
} from '@/service/content';
import { searchGamesApi } from '@/service/game';
import { useAppDispatch } from '@/store';
import { startArticleProgressTrack } from '@/store/modules/articleProgress';
import { useGoBack } from '@/hooks/useGoBack';
import { getArticleProgressResultMessage } from '@/utils/articleProgressMessage';
import { useAppSelector } from '@/store';
import { formatApiError } from '@/utils/apiError';
import { stripBodyImageMarkers } from '@/utils/bodyImageMarker';
import { mapGameTagsFromRaw, mergeGameTagOptions } from '@/utils/mapGameTag';
import { resolveGameCoverUrl } from '@/utils/steamImage';
import type { IGameListItem, IGameTag } from '@/types/game';
import type { EditorImage } from '@/views/PostEditor/types';
import CoverImageManager from '@/views/PostEditor/components/CoverImageManager';
import PageSubTopBar from '@/base-ui/PageSubTopBar';
import {
  createImageId,
  dedupeUrls,
  revokeBlobUrl,
} from '@/views/PostEditor/utils';

import './style.less';

const { TextArea } = Input;

const MODE_OPTIONS = [
  { label: '图文', value: POST_TYPE.IMAGE_TEXT },
  { label: '视频', value: POST_TYPE.VIDEO },
];

const VIDEO_MAX = 500 * 1024 * 1024;
const COVER_MAX = 9;

const BODY_SPACE_RE = /[ \u00a0\u3000]/g;

function compactBodyContent(value: string): string {
  return value
    .replace(BODY_SPACE_RE, '\n')
    .replace(/^\n+/, '')
    .replace(/\n{2,}/g, '\n');
}

function stripSpaces(value: string): string {
  return value.replace(BODY_SPACE_RE, '');
}

function createSkipComposingNormalize(
  composingRef: React.MutableRefObject<boolean>,
  normalize: (value: string) => string,
) {
  return (value: unknown) => {
    if (composingRef.current) return value;
    if (typeof value !== 'string') return value;
    return normalize(value);
  };
}

function canInsertBodyNewline(
  value: string,
  start: number,
  end: number,
): boolean {
  const before = value.slice(0, start);
  const after = value.slice(end);

  if (!value.replace(/\n/g, '')) return false;
  if (before.endsWith('\n') || after.startsWith('\n')) return false;

  return true;
}

function insertBodyNewlineAt(
  el: HTMLTextAreaElement,
  setContent: (value: string) => void,
  start: number,
  end: number,
) {
  const value = el.value;
  const next = compactBodyContent(
    `${value.slice(0, start)}\n${value.slice(end)}`,
  );
  setContent(next);

  const pos = Math.min(start + 1, next.length);
  requestAnimationFrame(() => {
    el.focus();
    el.setSelectionRange(pos, pos);
  });
}

function handleTitleKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
  if (e.nativeEvent.isComposing) return;
  if (e.key !== ' ' && e.code !== 'Space') return;
  e.preventDefault();
}

function handleBodyContentKeyDown(
  e: React.KeyboardEvent<HTMLTextAreaElement>,
  setContent: (value: string) => void,
) {
  if (e.nativeEvent.isComposing) return;

  const isNewlineKey = e.key === 'Enter' || e.key === ' ' || e.code === 'Space';
  if (!isNewlineKey) return;

  e.preventDefault();
  const el = e.currentTarget;
  const start = el.selectionStart ?? 0;
  const end = el.selectionEnd ?? 0;

  if (!canInsertBodyNewline(el.value, start, end)) return;

  insertBodyNewlineAt(el, setContent, start, end);
}

function scheduleFieldNormalize(
  form: ReturnType<typeof Form.useForm>[0],
  field: 'title' | 'content',
  normalize: (value: string) => string,
) {
  requestAnimationFrame(() => {
    const value = form.getFieldValue(field);
    if (typeof value !== 'string') return;
    const next = normalize(value);
    if (next !== value) {
      form.setFieldValue(field, next);
    }
  });
}

function createTitleFieldHandlers(
  form: ReturnType<typeof Form.useForm>[0],
  composingRef: React.MutableRefObject<boolean>,
) {
  return {
    onCompositionStart: () => {
      composingRef.current = true;
    },
    onCompositionEnd: (e: React.CompositionEvent<HTMLInputElement>) => {
      composingRef.current = false;
      scheduleFieldNormalize(form, 'title', stripSpaces);
    },
    onBlur: () => {
      if (composingRef.current) return;
      scheduleFieldNormalize(form, 'title', stripSpaces);
    },
    onKeyDown: (e: React.KeyboardEvent<HTMLInputElement>) => {
      if (composingRef.current) return;
      handleTitleKeyDown(e);
    },
  };
}

function createContentFieldHandlers(
  form: ReturnType<typeof Form.useForm>[0],
  composingRef: React.MutableRefObject<boolean>,
) {
  return {
    onCompositionStart: () => {
      composingRef.current = true;
    },
    onCompositionEnd: () => {
      composingRef.current = false;
      scheduleFieldNormalize(form, 'content', compactBodyContent);
    },
    onBlur: () => {
      if (composingRef.current) return;
      scheduleFieldNormalize(form, 'content', compactBodyContent);
    },
    onKeyDown: (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
      if (composingRef.current) return;
      handleBodyContentKeyDown(e, (value) =>
        form.setFieldValue('content', value),
      );
    },
  };
}

function listItemToGameTag(item: IGameListItem): IGameTag {
  return {
    appId: item.appId,
    name: item.name,
    iconUrl: resolveGameCoverUrl(item.appId, item.coverUrl),
  };
}

/** 多选 Select：空输入时 Enter 切换下拉，避免误删已选；有搜索词时交给 Select 选中项 */
function createMultiSelectEnterKeyDown(
  setOpen: React.Dispatch<React.SetStateAction<boolean>>,
) {
  return (e: React.KeyboardEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    if (e.key !== 'Enter') return;

    const searchValue = e.currentTarget.value?.trim() ?? '';
    if (searchValue) {
      e.stopPropagation();
      return;
    }

    e.preventDefault();
    e.stopPropagation();
    setOpen((prev) => !prev);
  };
}

const PostEditor: FC = () => {
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  const goBack = useGoBack('draft');
  const { user } = useAppSelector((state) => state.auth);
  const [searchParams] = useSearchParams();
  const editIdParam = searchParams.get('id');
  const gameAppIdParam = searchParams.get('gameAppId');
  const gameNameParam = searchParams.get('gameName');
  const lockedGameAppId = gameAppIdParam ? Number(gameAppIdParam) : undefined;
  const lockedGameName = gameNameParam?.trim() || undefined;
  const [form] = Form.useForm();
  const [postType, setPostType] = useState<PostType>(POST_TYPE.IMAGE_TEXT);
  const [categories, setCategories] = useState<ICategory[]>([]);
  const [coverImages, setCoverImages] = useState<EditorImage[]>([]);
  const [videoPendingUrl, setVideoPendingUrl] = useState<string | null>(null);
  const [videoPreviewUrl, setVideoPreviewUrl] = useState<string | null>(null);
  const [pendingVideoFile, setPendingVideoFile] = useState<File | null>(null);
  const [videoName, setVideoName] = useState<string | null>(null);
  const [uploadPercent, setUploadPercent] = useState(0);
  const [uploading, setUploading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [articleId, setArticleId] = useState<string | undefined>();
  const [articleStatus, setArticleStatus] = useState<number | undefined>();
  const [loadingArticle, setLoadingArticle] = useState(false);
  const [gameOptions, setGameOptions] = useState<IGameTag[]>([]);
  const [gameSearchLoading, setGameSearchLoading] = useState(false);
  const [categorySelectOpen, setCategorySelectOpen] = useState(false);
  const [gameSelectOpen, setGameSelectOpen] = useState(false);
  const abortRef = useRef<AbortController | null>(null);
  const uploadIdRef = useRef<string | null>(null);
  const gameSearchTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const titleComposingRef = useRef(false);
  const contentComposingRef = useRef(false);

  const handleCategoryEnterKeyDown = createMultiSelectEnterKeyDown(
    setCategorySelectOpen,
  );
  const handleGameEnterKeyDown =
    createMultiSelectEnterKeyDown(setGameSelectOpen);

  const gameAppIds = Form.useWatch<number[]>('gameAppIds', form) ?? [];

  useEffect(
    () => () => {
      coverImages.forEach((img) => revokeBlobUrl(img.previewUrl));
      revokeBlobUrl(videoPreviewUrl);
    },
    [coverImages, videoPreviewUrl],
  );

  useEffect(() => {
    listCategoriesApi()
      .then((res) => setCategories(res.data || []))
      .catch((err) => message.error(formatApiError('分区加载失败', err)));
  }, []);

  useEffect(
    () => () => {
      if (gameSearchTimerRef.current) {
        clearTimeout(gameSearchTimerRef.current);
      }
    },
    [],
  );

  useEffect(() => {
    if (editIdParam) return;
    if (!lockedGameAppId || !Number.isFinite(lockedGameAppId)) return;

    const lockedGame: IGameTag = {
      appId: lockedGameAppId,
      name: lockedGameName || `游戏 ${lockedGameAppId}`,
      iconUrl: resolveGameCoverUrl(lockedGameAppId),
    };
    setGameOptions((prev) => mergeGameTagOptions(prev, [lockedGame]));
    form.setFieldsValue({ gameAppIds: [lockedGameAppId] });
  }, [editIdParam, form, lockedGameAppId, lockedGameName]);

  const handleGameSearch = (keyword: string) => {
    if (gameSearchTimerRef.current) {
      clearTimeout(gameSearchTimerRef.current);
    }

    const trimmed = keyword.trim();
    if (!trimmed) return;

    gameSearchTimerRef.current = setTimeout(() => {
      setGameSearchLoading(true);
      void searchGamesApi(trimmed, { page: 1, size: 20 })
        .then((res) => {
          const items = (res.data || []).map(listItemToGameTag);
          setGameOptions((prev) => mergeGameTagOptions(prev, items));
        })
        .catch(() => {
          message.error('搜索游戏失败');
        })
        .finally(() => {
          setGameSearchLoading(false);
        });
    }, 300);
  };

  const handleGameChange = (nextIds: number[]) => {
    if (
      lockedGameAppId &&
      Number.isFinite(lockedGameAppId) &&
      !nextIds.includes(lockedGameAppId)
    ) {
      form.setFieldsValue({ gameAppIds: [lockedGameAppId, ...nextIds] });
      return;
    }
    form.setFieldsValue({ gameAppIds: nextIds });
  };

  useEffect(() => {
    if (!editIdParam) return undefined;
    const id = editIdParam;
    if (!id) return undefined;

    let cancelled = false;
    (async () => {
      setLoadingArticle(true);
      try {
        const [detailRes] = await Promise.all([loadArticleForEditApi(id)]);
        if (cancelled) return;
        const data = detailRes.data;
        if (!data) {
          message.error('内容不存在或无权编辑');
          navigate('/profile', { replace: true });
          return;
        }
        if (
          user?.accountId != null &&
          data.authorAccountId != null &&
          Number(user.accountId) !== data.authorAccountId
        ) {
          message.error('无权编辑他人内容');
          navigate('/profile', { replace: true });
          return;
        }
        if (data.postType === POST_TYPE.REPOST) {
          message.warning('转发动态暂不支持编辑');
          navigate(`/post/${id}`, { replace: true });
          return;
        }

        const rawType = data.postType || POST_TYPE.IMAGE_TEXT;
        const type =
          rawType === POST_TYPE.VIDEO ? POST_TYPE.VIDEO : POST_TYPE.IMAGE_TEXT;
        const content = compactBodyContent(
          stripBodyImageMarkers(data.content || ''),
        );
        const allUrls = dedupeUrls([
          ...(data.coverUrl ? [data.coverUrl] : []),
          ...(data.imageUrls || []),
        ]);

        setArticleId(data.publicId);
        setArticleStatus(data.status);
        setPostType(type);
        form.setFieldsValue({
          title: stripSpaces(data.title || ''),
          summary: data.summary,
          content,
          categoryIds:
            data.categoryIds && data.categoryIds.length > 0
              ? data.categoryIds
              : data.categoryId
                ? [data.categoryId]
                : [],
          gameAppIds: mapGameTagsFromRaw(data.gameTags)?.map(
            (tag) => tag.appId,
          ),
        });

        const editGameTags = mapGameTagsFromRaw(data.gameTags);
        if (editGameTags?.length) {
          setGameOptions((prev) => mergeGameTagOptions(prev, editGameTags));
        }

        if (data.videoUrl) {
          setVideoPendingUrl(data.videoUrl);
          setVideoPreviewUrl(data.videoUrl);
        }

        if (allUrls.length > 0) {
          setCoverImages(
            allUrls.map((url) => ({
              id: createImageId(),
              pendingUrl: url,
              previewUrl: url,
            })),
          );
        }
      } catch (err) {
        if (!cancelled) {
          message.error(formatApiError('加载内容失败', err));
          navigate('/profile', { replace: true });
        }
      } finally {
        if (!cancelled) setLoadingArticle(false);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [editIdParam, form, navigate, user?.accountId]);

  const clearVideoMedia = () => {
    abortRef.current?.abort();
    setVideoPreviewUrl((prev) => {
      revokeBlobUrl(prev);
      return null;
    });
    setPendingVideoFile(null);
    setVideoPendingUrl(null);
    setVideoName(null);
    setUploadPercent(0);
    uploadIdRef.current = null;
  };

  const trimCoverImages = (max: number) => {
    setCoverImages((prev) => {
      if (prev.length <= max) return prev;
      prev.slice(max).forEach((img) => revokeBlobUrl(img.previewUrl));
      return prev.slice(0, max);
    });
  };

  const onModeChange = (value: string | number) => {
    const next = Number(value) as PostType;
    if (next === postType) return;

    if (next === POST_TYPE.VIDEO) {
      trimCoverImages(1);
    } else {
      clearVideoMedia();
    }

    setPostType(next);
  };

  const buildPayload = (
    asDraft: boolean,
    media?: {
      coverUrl?: string | null;
      videoUrl?: string | null;
      imageUrls?: string[];
    },
  ) => {
    const values = form.getFieldsValue();
    const imageUrls = media?.imageUrls ?? [];
    const coverUrl = media?.coverUrl ?? imageUrls[0] ?? null;
    const savePostType =
      postType === POST_TYPE.VIDEO ? POST_TYPE.VIDEO : POST_TYPE.IMAGE_TEXT;

    return {
      id: articleId,
      title: values.title?.trim(),
      summary: values.summary?.trim(),
      content: compactBodyContent(
        stripBodyImageMarkers(values.content?.trim() || ''),
      ),
      categoryIds: values.categoryIds,
      categoryId: values.categoryIds?.[0],
      postType: savePostType,
      gameAppIds: values.gameAppIds?.length > 0 ? values.gameAppIds : undefined,
      coverUrl,
      videoUrl:
        savePostType === POST_TYPE.VIDEO
          ? (media?.videoUrl ?? videoPendingUrl)
          : null,
      imageUrls,
      status: asDraft ? ARTICLE_STATUS.DRAFT : ARTICLE_STATUS.PENDING,
    };
  };

  const hasVideoSource = Boolean(pendingVideoFile || videoPendingUrl);

  const uploadEditorImages = async (images: EditorImage[]) => {
    const pending = images.filter((img) => img.file);
    if (pending.length === 0) {
      return {
        urls: images.map((img) => img.pendingUrl).filter(Boolean) as string[],
        nextImages: images,
      };
    }

    const res = await uploadImagesApi(pending.map((img) => img.file!));
    let uploadIdx = 0;
    const nextImages: EditorImage[] = [];
    const urls: string[] = [];

    images.forEach((img) => {
      if (img.file) {
        const item = res.data[uploadIdx];
        uploadIdx += 1;
        if (!item) return;
        revokeBlobUrl(img.previewUrl);
        nextImages.push({
          ...img,
          file: undefined,
          pendingUrl: item.pendingUrl,
          previewUrl: item.previewUrl,
        });
        urls.push(item.pendingUrl);
        return;
      }
      if (img.pendingUrl) {
        nextImages.push(img);
        urls.push(img.pendingUrl);
      }
    });

    return { urls, nextImages };
  };

  /** 保存/提交时统一上传本地媒体 */
  const uploadPendingMedia = async (): Promise<{
    coverUrl: string | null;
    videoUrl: string | null;
    imageUrls: string[];
  }> => {
    let videoUrl = videoPendingUrl;

    const coverResult = await uploadEditorImages(coverImages);
    setCoverImages(coverResult.nextImages);

    const imageUrls = coverResult.urls;
    const coverUrl = imageUrls[0] || null;

    if (pendingVideoFile) {
      abortRef.current?.abort();
      const controller = new AbortController();
      abortRef.current = controller;
      setUploading(true);
      setUploadPercent(0);
      try {
        const media = await uploadVideoWithProgress(
          pendingVideoFile,
          setUploadPercent,
          {
            signal: controller.signal,
            onUploadId: (id) => {
              uploadIdRef.current = id;
            },
          },
        );
        videoUrl = media.pendingUrl;
        setVideoPendingUrl(media.pendingUrl);
        setVideoPreviewUrl(media.previewUrl);
        setPendingVideoFile(null);
      } finally {
        setUploading(false);
      }
    }

    return { coverUrl, videoUrl, imageUrls };
  };

  const coverMaxCount = postType === POST_TYPE.VIDEO ? 1 : COVER_MAX;

  const selectVideo = (file: File) => {
    const name = file.name.toLowerCase();
    if (!(name.endsWith('.mp4') || name.endsWith('.webm'))) {
      message.error('视频仅支持 mp4/webm');
      return Upload.LIST_IGNORE;
    }
    if (file.size > VIDEO_MAX) {
      message.error('视频不能超过 500MB');
      return Upload.LIST_IGNORE;
    }
    revokeBlobUrl(videoPreviewUrl);
    setPendingVideoFile(file);
    setVideoPendingUrl(null);
    setVideoPreviewUrl(URL.createObjectURL(file));
    setVideoName(file.name);
    setUploadPercent(0);
    return false;
  };

  const clearLocalVideo = () => {
    abortRef.current?.abort();
    revokeBlobUrl(videoPreviewUrl);
    setPendingVideoFile(null);
    setVideoPendingUrl(null);
    setVideoPreviewUrl(null);
    setVideoName(null);
    setUploadPercent(0);
    uploadIdRef.current = null;
  };

  const onSave = async (asDraft: boolean) => {
    try {
      await form.validateFields(
        asDraft || postType === POST_TYPE.VIDEO
          ? ['title', 'categoryIds']
          : ['title', 'categoryIds', 'content'],
      );
      if (postType === POST_TYPE.VIDEO && !hasVideoSource) {
        message.warning('请选择视频');
        return;
      }
      if (uploading) {
        message.warning('正在上传，请稍候');
        return;
      }
      setSubmitting(true);
      const media = await uploadPendingMedia();
      const payload = buildPayload(asDraft, media);
      const res = await saveArticleApi(payload);
      if (uploadIdRef.current) {
        await bindChunkUploadApi(uploadIdRef.current, res.data);
      }
      const title = payload.title || '内容';
      setArticleId(res.data);
      if (asDraft) {
        setArticleStatus(ARTICLE_STATUS.DRAFT);
        const { type, text } = getArticleProgressResultMessage(
          ARTICLE_STATUS.DRAFT,
          title,
          'draft',
        );
        message.open({ type, content: text, duration: 1 });
        goBack();
      } else {
        setArticleStatus(ARTICLE_STATUS.PENDING);
        dispatch(
          startArticleProgressTrack({
            articleId: res.data,
            title,
            kind: 'audit',
          }),
        );
        goBack();
      }
    } catch (err) {
      if ((err as { errorFields?: unknown })?.errorFields) return;
      if ((err as DOMException)?.name === 'AbortError') {
        message.info('已取消上传');
        return;
      }
      message.error({ content: formatApiError('保存失败', err), duration: 1 });
    } finally {
      setSubmitting(false);
    }
  };

  const onCancelPublish = () => {
    if (!articleId) return;
    Modal.confirm({
      title: '取消上架？',
      content:
        '将停止上传并取消审核任务，已上传文件会保留，状态改为已取消上架。',
      okText: '取消上架',
      okButtonProps: { danger: true },
      cancelText: '再想想',
      onOk: async () => {
        abortRef.current?.abort();
        await unpublishArticleApi(articleId);
        setUploading(false);
        const { type, text } = getArticleProgressResultMessage(
          ARTICLE_STATUS.OFFLINE,
          form.getFieldValue('title') || '内容',
          'unpublish',
        );
        message.open({ type, content: text, duration: 1 });
        goBack();
      },
    });
  };

  const onDelete = () => {
    if (!articleId) return;
    Modal.confirm({
      title: '删除内容？',
      content: '将停止上传、取消审核，并删除 MinIO 中的相关文件，不可恢复。',
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '再想想',
      onOk: async () => {
        abortRef.current?.abort();
        await deleteArticleApi(articleId);
        message.success({ content: '已删除', duration: 1 });
        goBack();
      },
    });
  };

  const videoCoverPreview =
    coverImages[0]?.previewUrl || coverImages[0]?.pendingUrl || undefined;

  const isEditing = Boolean(articleId);
  const isPublished = articleStatus === ARTICLE_STATUS.PUBLISHED;
  const isPending = articleStatus === ARTICLE_STATUS.PENDING;
  const isImageTextMode = postType !== POST_TYPE.VIDEO;
  const pageTitle = isEditing ? '编辑内容' : '创作中心';

  const modeSwitcher = (
    <Segmented
      options={MODE_OPTIONS}
      value={
        postType === POST_TYPE.VIDEO ? POST_TYPE.VIDEO : POST_TYPE.IMAGE_TEXT
      }
      onChange={onModeChange}
      disabled={uploading || isEditing}
    />
  );

  const topBarDock = (
    <div className="post-editor__top-dock">
      <div className="post-editor__align-track">
        <PageSubTopBar title={pageTitle} onBack={goBack} extra={modeSwitcher} />
      </div>
    </div>
  );

  if (loadingArticle) {
    return (
      <div className="post-editor post-editor--loading">
        {topBarDock}
        <PageLoading />
      </div>
    );
  }

  return (
    <div className="post-editor">
      {topBarDock}

      <Form
        form={form}
        layout="vertical"
        className="post-editor__form"
        requiredMark={false}
      >
        <Form.Item
          name="title"
          label="标题"
          normalize={createSkipComposingNormalize(
            titleComposingRef,
            stripSpaces,
          )}
          rules={[
            { required: true, message: '请输入标题' },
            { max: 80, message: '最多 80 字' },
          ]}
        >
          <Input
            placeholder="写一个吸引人的标题"
            maxLength={80}
            showCount
            allowClear
            {...createTitleFieldHandlers(form, titleComposingRef)}
          />
        </Form.Item>

        <Form.Item
          name="categoryIds"
          label="分区"
          rules={[
            {
              validator: (_, value: number[] | undefined) => {
                if (!value || value.length === 0) {
                  return Promise.reject(new Error('请选择分区'));
                }
                if (value.length > 3) {
                  return Promise.reject(new Error('最多选择 3 个分区'));
                }
                return Promise.resolve();
              },
            },
          ]}
        >
          <Select
            mode="multiple"
            placeholder="选择分区（最多 3 个）"
            maxCount={3}
            open={categorySelectOpen}
            onOpenChange={setCategorySelectOpen}
            options={categories.map((c) => ({ label: c.name, value: c.id }))}
            onInputKeyDown={handleCategoryEnterKeyDown}
          />
        </Form.Item>

        <Form.Item name="gameAppIds" label="关联游戏">
          <Select
            mode="multiple"
            allowClear={!lockedGameAppId}
            placeholder="搜索游戏名称并添加（可选）"
            showSearch
            filterOption={false}
            open={gameSelectOpen}
            onOpenChange={setGameSelectOpen}
            onSearch={handleGameSearch}
            onChange={handleGameChange}
            loading={gameSearchLoading}
            maxCount={5}
            notFoundContent={
              gameSearchLoading ? <Spin size="small" /> : '输入关键词搜索游戏'
            }
            onInputKeyDown={handleGameEnterKeyDown}
            options={gameOptions.map((game) => ({
              value: game.appId,
              label: game.name,
              game,
            }))}
            optionRender={(option) => {
              const game = option.data.game as IGameTag;
              return (
                <div className="post-editor__game-option">
                  {game.iconUrl ? (
                    <img
                      src={game.iconUrl}
                      alt=""
                      className="post-editor__game-option-icon"
                    />
                  ) : null}
                  <span>{game.name}</span>
                </div>
              );
            }}
            tagRender={(props) => {
              const { label, value, closable, onClose } = props;
              const game = gameOptions.find((item) => item.appId === value);
              const locked =
                lockedGameAppId != null && value === lockedGameAppId;
              return (
                <Tag
                  className="post-editor__game-tag"
                  closable={closable && !locked}
                  onClose={onClose}
                >
                  {game?.iconUrl ? (
                    <img
                      src={game.iconUrl}
                      alt=""
                      className="post-editor__game-tag-icon"
                    />
                  ) : null}
                  <span>{label}</span>
                </Tag>
              );
            }}
          />
        </Form.Item>

        {(isImageTextMode || postType === POST_TYPE.VIDEO) && (
          <Form.Item
            label={
              postType === POST_TYPE.VIDEO
                ? '视频封面（可选）'
                : '顶部封面（可选，最多 9 张）'
            }
            extra={
              isImageTextMode
                ? '可上传多张或从关联游戏选封面；支持裁剪与排序。未上传时将自动生成标题海报'
                : undefined
            }
          >
            <CoverImageManager
              images={coverImages}
              onChange={setCoverImages}
              maxCount={coverMaxCount}
              multiple={isImageTextMode}
              gameAppIds={gameAppIds}
              gameOptions={gameOptions}
            />
          </Form.Item>
        )}

        {postType === POST_TYPE.VIDEO && (
          <Form.Item label="视频" required>
            <Space direction="vertical" style={{ width: '100%' }}>
              <Upload
                accept="video/mp4,video/webm,.mp4,.webm"
                maxCount={1}
                showUploadList={false}
                beforeUpload={selectVideo}
                disabled={uploading || isPending}
              >
                <Button icon={<VideoCameraOutlined />} disabled={isPending}>
                  {videoName || pendingVideoFile || videoPendingUrl
                    ? '重新选择视频'
                    : '选择视频（保存时上传，≤500MB）'}
                </Button>
              </Upload>
              {(uploading || pendingVideoFile || videoPendingUrl) && (
                <div className="post-editor__progress">
                  {uploading && (
                    <Progress percent={uploadPercent} status="active" />
                  )}
                  {videoName && (
                    <span className="post-editor__hint">
                      {videoName}
                      {!uploading && !videoPendingUrl ? '（待上传）' : ''}
                    </span>
                  )}
                </div>
              )}
              {videoPreviewUrl && !uploading && (
                <div className="post-editor__player-row">
                  <div className="post-editor__player">
                    <VideoPlayer
                      url={videoPreviewUrl}
                      pic={videoCoverPreview}
                      title={videoName || '视频预览'}
                      mode="inline"
                    />
                  </div>
                  <button
                    type="button"
                    className="post-editor__video-del"
                    onClick={clearLocalVideo}
                    aria-label="删除视频"
                  >
                    <span className="post-editor__video-del-icon">
                      <DeleteOutlined />
                    </span>
                    <span className="post-editor__video-del-text">删除</span>
                  </button>
                </div>
              )}
            </Space>
          </Form.Item>
        )}

        <Form.Item
          name="content"
          label={isImageTextMode ? '正文' : '视频介绍'}
          normalize={createSkipComposingNormalize(
            contentComposingRef,
            compactBodyContent,
          )}
          rules={
            isImageTextMode
              ? [
                  { required: true, whitespace: true, message: '请输入正文' },
                  { max: 8000, message: '正文最多 8000 字' },
                ]
              : [{ max: 8000, message: '介绍最多 8000 字' }]
          }
        >
          <TextArea
            rows={isImageTextMode ? 12 : 8}
            placeholder={
              isImageTextMode ? '写下你想分享的内容' : '介绍一下你的视频'
            }
            maxLength={8000}
            showCount
            allowClear
            {...createContentFieldHandlers(form, contentComposingRef)}
          />
        </Form.Item>

        <Form.Item name="summary" label="摘要（可选）">
          <Input.TextArea
            rows={2}
            maxLength={200}
            showCount
            allowClear
            placeholder="列表摘要，不填则自动截取正文"
          />
        </Form.Item>

        <div className="post-editor__actions">
          <Space>
            <Button
              onClick={() => onSave(true)}
              loading={submitting || uploading}
            >
              保存草稿
            </Button>
            <Button
              type="primary"
              onClick={() => onSave(false)}
              loading={submitting || uploading}
              disabled={isPending}
            >
              {isPublished ? '保存并提交审核' : '提交审核'}
            </Button>
            {articleId && (
              <>
                <Button danger onClick={onCancelPublish}>
                  取消上架
                </Button>
                <Button danger type="primary" ghost onClick={onDelete}>
                  删除
                </Button>
              </>
            )}
          </Space>
          <p className="post-editor__tip">
            取消上架：停上传、取消审核、改状态，文件保留。删除：额外清空 MinIO
            文件。
          </p>
        </div>
      </Form>
    </div>
  );
};

export default PostEditor;
