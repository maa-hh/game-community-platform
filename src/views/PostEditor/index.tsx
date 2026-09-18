import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import type { FC } from 'react';
import {
  App,
  Button,
  Form,
  Input,
  Modal,
  Select,
  Segmented,
  Space,
  Spin,
  Tag,
  Upload,
} from 'antd';
import { DeleteOutlined, VideoCameraOutlined } from '@ant-design/icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import ReactQuill from 'react-quill-new';
import 'react-quill-new/dist/quill.snow.css';

import VideoPlayer from '@/base-ui/VideoPlayer';
import PageLoading from '@/base-ui/PageLoading';
import {
  ARTICLE_STATUS,
  POST_TYPE,
  deleteArticleApi,
  listCategoriesApi,
  loadArticleForEditApi,
  saveArticleApi,
  unpublishArticleApi,
  type ICategory,
  type PostType,
} from '@/service/content';
import { searchGamesApi } from '@/service/game';
import { useGoBack } from '@/hooks/useGoBack';
import { getArticleProgressResultMessage } from '@/utils/articleProgressMessage';
import { invalidateOwnProfilePostCaches } from '@/utils/profileDataCache';
import { useAppSelector } from '@/store';
import { formatApiError } from '@/utils/apiError';
import { mapGameTagsFromRaw, mergeGameTagOptions } from '@/utils/mapGameTag';
import { resolveGameCoverUrl } from '@/utils/steamImage';
import { richHtmlToParagraphs, richHtmlToPlainText } from '@/utils/richText';
import {
  captureVideoFrame,
  captureVideoFrameFromUrl,
} from '@/utils/videoPoster';
import type { IGameListItem, IGameTag } from '@/types/game';
import type { EditorImage } from '@/views/PostEditor/types';
import CoverImageManager from '@/views/PostEditor/components/CoverImageManager';
import PageSubTopBar from '@/base-ui/PageSubTopBar';
import {
  createImageId,
  dedupeUrls,
  revokeBlobUrl,
} from '@/views/PostEditor/utils';
import {
  discardPersistentPostUpload,
  getPersistentPostUploadTask,
  getPersistentPostUploadPromise,
  startPersistentPostUpload,
  updatePersistentPostUploadPayload,
  type PersistentPostUploadFile,
} from '@/utils/persistentPostUpload';

import './style.less';

const MODE_OPTIONS = [
  { label: '图文', value: POST_TYPE.IMAGE_TEXT },
  { label: '视频', value: POST_TYPE.VIDEO },
];

const RICH_TEXT_TOOLBAR = [
  [{ header: [1, 2, 3, false] }],
  ['bold', 'italic', 'underline', 'strike'],
  [{ list: 'ordered' }, { list: 'bullet' }],
  ['blockquote', 'link'],
  ['clean'],
];

const RICH_TEXT_FORMATS = [
  'header',
  'bold',
  'italic',
  'underline',
  'strike',
  'list',
  'blockquote',
  'link',
];

const VIDEO_MAX = 500 * 1024 * 1024;
const COVER_MAX = 9;
const CONTENT_MAX_LENGTH = 3000;

const BODY_SPACE_RE = /[ \u00a0\u3000]/g;

function stripSpaces(value: string): string {
  return value.replace(BODY_SPACE_RE, '');
}

function plainTextToRichHtml(value: string): string {
  return value
    .split(/\n+/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map(
      (line) =>
        `<p>${line
          .replace(/&/g, '&amp;')
          .replace(/</g, '&lt;')
          .replace(/>/g, '&gt;')}</p>`,
    )
    .join('');
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

function handleTitleKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
  if (e.nativeEvent.isComposing) return;
  if (e.key !== ' ' && e.code !== 'Space') return;
  e.preventDefault();
}

function scheduleFieldNormalize(
  form: ReturnType<typeof Form.useForm>[0],
  field: 'title',
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
  const { message, modal } = App.useApp();
  const navigate = useNavigate();
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
  const [videoCoverGenerating, setVideoCoverGenerating] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [articleId, setArticleId] = useState<string | undefined>();
  const [articleStatus, setArticleStatus] = useState<number | undefined>();
  const [loadingArticle, setLoadingArticle] = useState(false);
  const [gameOptions, setGameOptions] = useState<IGameTag[]>([]);
  const [gameSearchLoading, setGameSearchLoading] = useState(false);
  const [categorySelectOpen, setCategorySelectOpen] = useState(false);
  const [gameSelectOpen, setGameSelectOpen] = useState(false);
  const [linkModalOpen, setLinkModalOpen] = useState(false);
  const [linkUrl, setLinkUrl] = useState('');
  const [richTextComposing, setRichTextComposing] = useState(false);
  const uploadTaskIdRef = useRef<string | null>(null);
  const videoCoverTokenRef = useRef(0);
  const autoVideoCoverPromiseRef = useRef<Promise<void> | null>(null);
  const autoVideoCoverFileRef = useRef<{
    file: File;
    token: number;
  } | null>(null);
  const gameSearchTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const titleComposingRef = useRef(false);
  const richQuillRef = useRef<ReactQuill | null>(null);
  const linkSelectionRef = useRef<{ index: number; length: number } | null>(
    null,
  );

  const handleCategoryEnterKeyDown = createMultiSelectEnterKeyDown(
    setCategorySelectOpen,
  );
  const handleGameEnterKeyDown =
    createMultiSelectEnterKeyDown(setGameSelectOpen);

  const gameAppIds = Form.useWatch<number[]>('gameAppIds', form) ?? [];
  const richContentHtml = Form.useWatch<string>('contentHtml', form) ?? '';
  const richContentLength = richHtmlToPlainText(richContentHtml).length;

  // 反馈统一走当前 Ant Design App 上下文，保证主题与容器配置一致。
  const handleLinkToolbar = useCallback(
    (value: boolean) => {
      const editor = richQuillRef.current?.getEditor();
      if (!editor) return;

      const range = editor.getSelection();
      if (!value) {
        if (range) editor.format('link', false, 'user');
        return;
      }
      if (!range) {
        message.info('请先点击编辑区，再添加链接');
        return;
      }

      linkSelectionRef.current = range;
      const selectedText =
        range.length > 0 ? editor.getText(range.index, range.length) : '';
      setLinkUrl(/^https?:\/\//i.test(selectedText) ? selectedText : '');
      setLinkModalOpen(true);
    },
    [message],
  );

  const handleCleanToolbar = useCallback(() => {
    const editor = richQuillRef.current?.getEditor();
    const range = editor?.getSelection();
    if (!editor || !range || range.length === 0) {
      message.info('请先选中要清除格式的文字');
      return;
    }
    editor.removeFormat(range.index, range.length, 'user');
  }, [message]);

  const richTextModules = useMemo(
    () => ({
      toolbar: {
        container: RICH_TEXT_TOOLBAR,
        handlers: {
          link: handleLinkToolbar,
          clean: handleCleanToolbar,
        },
      },
    }),
    [handleCleanToolbar, handleLinkToolbar],
  );

  const handleLinkModalOk = () => {
    const rawUrl = linkUrl.trim();
    if (!rawUrl) {
      message.error('请输入链接地址');
      return;
    }
    if (/\s/.test(rawUrl)) {
      message.error('链接地址不能包含空格');
      return;
    }

    const url = /^(?:https?:\/\/|mailto:)/i.test(rawUrl)
      ? rawUrl
      : `https://${rawUrl}`;
    const editor = richQuillRef.current?.getEditor();
    const range = linkSelectionRef.current;
    if (!editor || !range) {
      setLinkModalOpen(false);
      return;
    }

    editor.focus();
    if (range.length > 0) {
      editor.formatText(range.index, range.length, 'link', url, 'user');
    } else {
      editor.insertText(range.index, rawUrl, { link: url }, 'user');
      editor.setSelection(range.index + rawUrl.length, 0, 'silent');
    }
    setLinkModalOpen(false);
  };

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
  }, [message]);

  useEffect(
    () => () => {
      if (gameSearchTimerRef.current) {
        clearTimeout(gameSearchTimerRef.current);
      }
    },
    [],
  );

  useEffect(() => {
    const editor = richQuillRef.current?.getEditor();
    const editorRoot = editor?.root;
    if (!editorRoot) return undefined;

    const handleCompositionStart = () => setRichTextComposing(true);
    const handleCompositionEnd = () => setRichTextComposing(false);

    editorRoot.addEventListener('compositionstart', handleCompositionStart);
    editorRoot.addEventListener('compositionend', handleCompositionEnd);

    return () => {
      editorRoot.removeEventListener(
        'compositionstart',
        handleCompositionStart,
      );
      editorRoot.removeEventListener('compositionend', handleCompositionEnd);
    };
  }, [loadingArticle]);

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
        const legacyPlainContent =
          data.content?.trim() ||
          Object.values(data.contentParagraphs || {}).join('\n');
        const savedImageRefs = dedupeUrls([
          ...(data.coverRef
            ? [data.coverRef]
            : data.coverUrl
              ? [data.coverUrl]
              : []),
          ...(data.imageRefs || data.imageUrls || []),
        ]);
        const savedImagePreviews = dedupeUrls([
          ...(data.coverUrl ? [data.coverUrl] : []),
          ...(data.imageUrls || []),
        ]);

        setArticleId(data.publicId);
        setArticleStatus(data.status);
        setPostType(type);
        form.setFieldsValue({
          title: stripSpaces(data.title || ''),
          summary: data.summary,
          contentHtml:
            data.contentHtml || plainTextToRichHtml(legacyPlainContent),
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

        const savedVideoUrl = data.videoRef || data.videoUrl;
        if (savedVideoUrl) {
          setVideoPendingUrl(savedVideoUrl);
          setVideoPreviewUrl(data.videoUrl || data.videoRef || null);
        }

        if (savedImageRefs.length > 0) {
          setCoverImages(
            savedImageRefs.map((ref, index) => ({
              id: createImageId(),
              pendingUrl: ref,
              previewUrl: savedImagePreviews[index] || ref,
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
  }, [editIdParam, form, message, navigate, user?.accountId]);

  const cancelPersistentUpload = () => {
    const taskId = uploadTaskIdRef.current;
    if (!taskId) return;
    const task = getPersistentPostUploadTask(taskId);
    discardPersistentPostUpload(taskId);
    uploadTaskIdRef.current = null;
    if (!articleId && task?.articleId) {
      setArticleId(task.articleId);
      setArticleStatus(ARTICLE_STATUS.DRAFT);
    }
  };

  const clearVideoCoverState = () => {
    videoCoverTokenRef.current += 1;
    autoVideoCoverPromiseRef.current = null;
    autoVideoCoverFileRef.current = null;
    setVideoCoverGenerating(false);
    setCoverImages((previous) => {
      const retained = previous.filter((image) => image.source !== 'video');
      previous
        .filter((image) => image.source === 'video')
        .forEach((image) => revokeBlobUrl(image.previewUrl));
      return retained;
    });
  };

  const clearVideoMedia = () => {
    cancelPersistentUpload();
    clearVideoCoverState();
    setVideoPreviewUrl((prev) => {
      revokeBlobUrl(prev);
      return null;
    });
    setPendingVideoFile(null);
    setVideoPendingUrl(null);
    setVideoName(null);
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
    cancelPersistentUpload();

    if (next === POST_TYPE.VIDEO) {
      trimCoverImages(1);
    } else {
      clearVideoMedia();
    }

    setPostType(next);
  };

  const handleCoverImagesChange = (images: EditorImage[]) => {
    videoCoverTokenRef.current += 1;
    autoVideoCoverPromiseRef.current = null;
    autoVideoCoverFileRef.current = null;
    setVideoCoverGenerating(false);
    cancelPersistentUpload();
    setCoverImages(images);
  };

  const replaceWithVideoCover = (file: File) => {
    videoCoverTokenRef.current += 1;
    autoVideoCoverPromiseRef.current = null;
    autoVideoCoverFileRef.current = null;
    setVideoCoverGenerating(false);
    setCoverImages((previous) => {
      previous.forEach((image) => revokeBlobUrl(image.previewUrl));
      return [
        {
          id: createImageId(),
          file,
          previewUrl: URL.createObjectURL(file),
          source: 'video',
        },
      ];
    });
  };

  const startAutoVideoCover = (file: File): Promise<void> => {
    const token = ++videoCoverTokenRef.current;
    autoVideoCoverFileRef.current = null;
    setVideoCoverGenerating(true);
    const promise = captureVideoFrame(file, 1)
      .then((blob) => {
        if (!blob) throw new Error('视频封面生成失败');
        if (token !== videoCoverTokenRef.current) return;
        const coverFile = new File([blob], 'video-cover-auto.jpg', {
          type: 'image/jpeg',
        });
        autoVideoCoverFileRef.current = { file: coverFile, token };
        setCoverImages((previous) => {
          if (previous.length > 0 || token !== videoCoverTokenRef.current) {
            return previous;
          }
          return [
            {
              id: createImageId(),
              file: coverFile,
              previewUrl: URL.createObjectURL(coverFile),
              source: 'video',
            },
          ];
        });
      })
      .catch((error) => {
        if (token === videoCoverTokenRef.current) {
          autoVideoCoverPromiseRef.current = null;
          message.warning('自动生成封面失败，请手动从视频中选择');
        }
        throw error;
      })
      .finally(() => {
        if (token === videoCoverTokenRef.current) {
          setVideoCoverGenerating(false);
        }
      });
    autoVideoCoverPromiseRef.current = promise;
    void promise.catch(() => undefined);
    return promise;
  };

  const ensureVideoCover = async () => {
    if (!pendingVideoFile || coverImages.length > 0) return;
    const pending =
      autoVideoCoverPromiseRef.current || startAutoVideoCover(pendingVideoFile);
    await pending;
  };

  const buildPayload = (
    asDraft: boolean,
    values: Record<string, any>,
    media?: {
      coverUrl?: string | null;
      videoUrl?: string | null;
      imageUrls?: string[];
    },
  ) => {
    const imageUrls = media?.imageUrls ?? [];
    const coverUrl = media?.coverUrl ?? imageUrls[0] ?? null;
    const savePostType =
      postType === POST_TYPE.VIDEO ? POST_TYPE.VIDEO : POST_TYPE.IMAGE_TEXT;
    const contentHtml = values.contentHtml?.trim() || '';
    const plainContent = richHtmlToPlainText(contentHtml);

    return {
      id: articleId,
      title: stripSpaces(String(values.title || '')).trim(),
      summary: values.summary?.trim(),
      content: plainContent,
      contentHtml,
      contentParagraphs: richHtmlToParagraphs(contentHtml),
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

  const hasVideoSource = Boolean(
    pendingVideoFile || videoPendingUrl || uploadTaskIdRef.current,
  );

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
    cancelPersistentUpload();
    videoCoverTokenRef.current += 1;
    autoVideoCoverPromiseRef.current = null;
    autoVideoCoverFileRef.current = null;
    setVideoCoverGenerating(false);
    const retainedCovers = coverImages.filter(
      (image) => image.source !== 'video',
    );
    coverImages
      .filter((image) => image.source === 'video')
      .forEach((image) => revokeBlobUrl(image.previewUrl));
    if (retainedCovers.length !== coverImages.length) {
      setCoverImages(retainedCovers);
    }
    revokeBlobUrl(videoPreviewUrl);
    setPendingVideoFile(file);
    setVideoPendingUrl(null);
    setVideoPreviewUrl(URL.createObjectURL(file));
    setVideoName(file.name);
    if (retainedCovers.length === 0) {
      void startAutoVideoCover(file).catch(() => undefined);
    }
    return false;
  };

  const clearLocalVideo = () => {
    cancelPersistentUpload();
    clearVideoCoverState();
    revokeBlobUrl(videoPreviewUrl);
    setPendingVideoFile(null);
    setVideoPendingUrl(null);
    setVideoPreviewUrl(null);
    setVideoName(null);
  };

  const onSave = async (asDraft: boolean) => {
    try {
      const values = await form.validateFields(
        asDraft || postType === POST_TYPE.VIDEO
          ? ['title', 'categoryIds']
          : ['title', 'categoryIds', 'contentHtml'],
      );
      if (!asDraft && postType === POST_TYPE.VIDEO && !hasVideoSource) {
        message.warning('请选择视频');
        return;
      }
      const normalizedValues = {
        ...form.getFieldsValue(),
        ...values,
        title: stripSpaces(String(values.title || '')).trim(),
      };
      if (!normalizedValues.title) {
        form.setFields([{ name: 'title', errors: ['请输入标题'] }]);
        return;
      }
      const displayTitle = normalizedValues.title;
      setSubmitting(true);
      if (!asDraft) {
        // 提交审核先放弃旧草稿快照，已发布缓存等审核最终结果为 PUBLISHED 再失效。
        invalidateOwnProfilePostCaches(user?.accountId, ['draft']);
      }
      if (pendingVideoFile && coverImages.length === 0) {
        await ensureVideoCover();
      }
      let savedVideoCover: File | null = null;
      if (
        !pendingVideoFile &&
        videoPendingUrl &&
        coverImages.length === 0 &&
        videoPreviewUrl &&
        !videoPreviewUrl.startsWith('pending://')
      ) {
        try {
          setVideoCoverGenerating(true);
          const blob = await captureVideoFrameFromUrl(videoPreviewUrl, 1);
          if (!blob) throw new Error('视频封面生成失败');
          savedVideoCover = new File([blob], 'video-cover-auto.jpg', {
            type: 'image/jpeg',
          });
        } catch {
          message.error('无法自动生成视频封面，请先从视频中选择封面');
          return;
        } finally {
          setVideoCoverGenerating(false);
        }
      }
      const pendingFiles: PersistentPostUploadFile[] = coverImages
        .filter((image) => image.file)
        .map((image) => ({ kind: 'image' as const, file: image.file! }));
      const autoVideoCover =
        coverImages.length === 0 &&
        autoVideoCoverFileRef.current?.token === videoCoverTokenRef.current
          ? autoVideoCoverFileRef.current.file
          : null;
      if (autoVideoCover) {
        pendingFiles.unshift({ kind: 'image', file: autoVideoCover });
      }
      if (savedVideoCover) {
        pendingFiles.unshift({ kind: 'image', file: savedVideoCover });
      }
      if (pendingVideoFile) {
        pendingFiles.push({ kind: 'video', file: pendingVideoFile });
      }
      const existingImageUrls = coverImages
        .filter((image) => !image.file && image.pendingUrl)
        .map((image) => image.pendingUrl as string);
      const payload = buildPayload(asDraft, normalizedValues, {
        coverUrl: existingImageUrls[0] || null,
        videoUrl: videoPendingUrl,
        imageUrls: existingImageUrls,
      });
      let res: { data: string };
      if (uploadTaskIdRef.current) {
        updatePersistentPostUploadPayload(uploadTaskIdRef.current, payload);
        const completedTask = await getPersistentPostUploadPromise(
          uploadTaskIdRef.current,
        );
        res = { data: completedTask.articleId || '' };
      } else if (pendingFiles.length > 0) {
        if (user?.accountId == null) {
          throw new Error('登录状态已失效，请重新登录');
        }
        const operation = startPersistentPostUpload({
          accountId: user.accountId,
          payload,
          files: pendingFiles,
        });
        uploadTaskIdRef.current = operation.taskId;
        setUploading(true);
        setPendingVideoFile(null);
        const completedTask = await operation.promise;
        if (completedTask.uploadedVideo) {
          setVideoPendingUrl(completedTask.uploadedVideo.pendingUrl);
          setVideoPreviewUrl(completedTask.uploadedVideo.previewUrl);
        }
        uploadTaskIdRef.current = null;
        res = { data: completedTask.articleId || '' };
      } else {
        const saved = await saveArticleApi(payload);
        res = { data: saved.data };
      }
      if (!res.data) throw new Error('保存接口未返回文章 ID');
      const title = displayTitle;
      if (asDraft) {
        invalidateOwnProfilePostCaches(
          user?.accountId,
          articleStatus === ARTICLE_STATUS.PUBLISHED
            ? ['published', 'draft']
            : ['draft'],
        );
      }
      setArticleId(res.data);
      if (asDraft) {
        setArticleStatus(ARTICLE_STATUS.DRAFT);
        const { type, text } = getArticleProgressResultMessage(
          ARTICLE_STATUS.DRAFT,
          title,
          'draft',
        );
        message.open({ type, content: text, duration: 1 });
        navigate('/profile', {
          replace: true,
          state: { postSubTab: 'draft' },
        });
      } else {
        setArticleStatus(ARTICLE_STATUS.PENDING);
        navigate('/profile', {
          replace: true,
          state: { postSubTab: 'draft' },
        });
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
      setUploading(false);
    }
  };

  const onCancelPublish = () => {
    if (!articleId) return;
    const published = articleStatus === ARTICLE_STATUS.PUBLISHED;
    modal.confirm({
      title: published ? '移入草稿箱？' : '取消上架？',
      content:
        '将停止上传并取消审核任务，内容仅作者可见，已上传文件会继续保留。',
      okText: published ? '移入草稿箱' : '取消上架',
      okButtonProps: { danger: true },
      cancelText: '再想想',
      onOk: async () => {
        cancelPersistentUpload();
        await unpublishArticleApi(articleId);
        setUploading(false);
        const { type, text } = getArticleProgressResultMessage(
          ARTICLE_STATUS.DRAFT,
          form.getFieldValue('title') || '内容',
          'unpublish',
        );
        message.open({ type, content: text, duration: 1 });
        invalidateOwnProfilePostCaches(
          user?.accountId,
          published ? ['published', 'draft'] : ['draft'],
        );
        goBack();
      },
    });
  };

  const onDelete = () => {
    if (!articleId) return;
    modal.confirm({
      title: '删除内容？',
      content: '将停止上传、取消审核，并删除 MinIO 中的相关文件，不可恢复。',
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '再想想',
      onOk: async () => {
        cancelPersistentUpload();
        await deleteArticleApi(articleId);
        message.success({ content: '已删除', duration: 1 });
        invalidateOwnProfilePostCaches(
          user?.accountId,
          isPublished ? ['published'] : ['draft'],
        );
        goBack();
      },
    });
  };

  const videoCoverPreview =
    coverImages[0]?.previewUrl || coverImages[0]?.pendingUrl || undefined;

  const isEditing = Boolean(articleId);
  const isPublished = articleStatus === ARTICLE_STATUS.PUBLISHED;
  const isPending = articleStatus === ARTICLE_STATUS.PENDING;
  const isImageTextMode = postType === POST_TYPE.IMAGE_TEXT;
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

      <Modal
        title="添加链接"
        open={linkModalOpen}
        okText="插入链接"
        cancelText="取消"
        onOk={handleLinkModalOk}
        onCancel={() => setLinkModalOpen(false)}
      >
        <Input
          autoFocus
          value={linkUrl}
          placeholder="https://example.com"
          onChange={(event) => setLinkUrl(event.target.value)}
          onPressEnter={handleLinkModalOk}
        />
      </Modal>

      <Form
        form={form}
        layout="vertical"
        className="post-editor__form"
        requiredMark={false}
      >
        <p className="post-editor__tip">
          {isImageTextMode
            ? '提示：服务器资源有限，请勿上传单张超过 5MB 的图片，图片总数不要超过 9 张。'
            : '提示：服务器资源有限，请勿上传超过 500MB 的视频。'}
        </p>
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
            open={gameSelectOpen}
            onOpenChange={setGameSelectOpen}
            showSearch={{
              filterOption: false,
              onSearch: handleGameSearch,
            }}
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
              onChange={handleCoverImagesChange}
              maxCount={coverMaxCount}
              multiple={isImageTextMode}
              disabled={uploading || isPending}
              gameAppIds={gameAppIds}
              gameOptions={gameOptions}
              videoFile={pendingVideoFile}
              videoUrl={
                pendingVideoFile ? null : videoPreviewUrl || videoPendingUrl
              }
              onVideoCoverConfirm={replaceWithVideoCover}
              videoCoverLoading={videoCoverGenerating}
            />
            {uploading && postType === POST_TYPE.IMAGE_TEXT && (
              <div className="post-editor__progress">
                <span className="post-editor__hint">
                  图片上传中，切换页面后任务仍会继续
                </span>
              </div>
            )}
          </Form.Item>
        )}

        {postType === POST_TYPE.VIDEO && (
          <Form.Item label="视频" required>
            <Space orientation="vertical" style={{ width: '100%' }}>
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
                  {videoName && (
                    <span className="post-editor__hint">
                      {videoName}
                      {uploading
                        ? '（上传中，切换页面后任务仍会继续）'
                        : !videoPendingUrl
                          ? '（待上传）'
                          : ''}
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
          name="contentHtml"
          label={isImageTextMode ? '正文' : '视频介绍'}
          extra={
            <div
              className={`post-editor__rich-count${
                richContentLength > CONTENT_MAX_LENGTH
                  ? ' post-editor__rich-count--over'
                  : ''
              }`}
            >
              已输入 {richContentLength} / {CONTENT_MAX_LENGTH} 字
            </div>
          }
          rules={[
            {
              validator: (_, value: string | undefined) => {
                const plainText = richHtmlToPlainText(value || '');
                if (!plainText) {
                  return Promise.reject(
                    new Error(
                      isImageTextMode ? '请输入正文' : '请输入视频介绍',
                    ),
                  );
                }
                if (plainText.length > CONTENT_MAX_LENGTH) {
                  return Promise.reject(
                    new Error(`最多 ${CONTENT_MAX_LENGTH} 字`),
                  );
                }
                return Promise.resolve();
              },
            },
          ]}
        >
          <ReactQuill
            ref={richQuillRef}
            className={`post-editor__rich-editor${
              richTextComposing ? ' post-editor__rich-editor--composing' : ''
            }`}
            theme="snow"
            modules={richTextModules}
            formats={RICH_TEXT_FORMATS}
            readOnly={isPending}
            placeholder={isImageTextMode ? '输入文章正文' : '介绍一下你的视频'}
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
            {articleId && (isPublished || isPending) && (
              <>
                <Button danger onClick={onCancelPublish}>
                  {isPublished ? '移入草稿箱' : '取消上架'}
                </Button>
                <Button danger type="primary" ghost onClick={onDelete}>
                  删除
                </Button>
              </>
            )}
          </Space>
          <p className="post-editor__tip">
            移入草稿箱：停上传、取消审核，仅作者可见，文件保留。删除：清空媒体文件。
          </p>
        </div>
      </Form>
    </div>
  );
};

export default PostEditor;
