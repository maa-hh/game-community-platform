import hyRequest from './request';
import {
  UPLOAD_CHUNK_CONCURRENCY,
  UPLOAD_CHUNK_MAX_RETRIES,
  UPLOAD_CHUNK_RETRY_BASE_DELAY_MS,
  UPLOAD_CHUNK_REQUEST_TIMEOUT_MS,
} from './config';
import type { IDataType, IPageResult } from './types';
import type { PostSubTabKey } from '@/types/profile';
import { calculateFileMd5 } from '@/utils/fileMd5';

/** 发帖模式 */
export const POST_TYPE = {
  IMAGE_TEXT: 1,
  ARTICLE: 2,
  VIDEO: 3,
  /** 转发动态（引用原帖 + 个人评论） */
  REPOST: 4,
} as const;

export type PostType = (typeof POST_TYPE)[keyof typeof POST_TYPE];

export const ARTICLE_STATUS = {
  DRAFT: 0,
  PUBLISHED: 1,
  PENDING: 2,
  OFFLINE: 3,
  REJECTED: 4,
} as const;

export const TASK_STATUS = {
  PENDING: 0,
  RUNNING: 1,
  COMPLETED: 2,
  FAILED: 3,
  CANCELLED: 4,
} as const;

export interface ICategory {
  id: number;
  name: string;
  description?: string;
  iconUrl?: string;
  status?: number;
  sort?: number;
}

export interface IMediaUpload {
  objectKey: string;
  pendingUrl: string;
  previewUrl: string;
}

export interface IChunkUploadInit {
  uploadId: string;
  objectKey?: string;
  chunkSize: number;
  totalChunks: number;
  uploadedChunks: number[];
  instant: boolean;
  pendingUrl?: string;
  previewUrl?: string;
}

export interface IChunkUploadStatus {
  uploadId: string;
  status: string;
  totalChunks: number;
  uploadedCount: number;
  uploadedChunks: number[];
  percent: number;
  pendingUrl?: string;
  previewUrl?: string;
}

export type ChunkUploadBizType = 'video' | 'cover' | 'image';

export interface IChunkUploadOptions {
  articleId?: number;
  signal?: AbortSignal;
  onUploadId?: (uploadId: string) => void;
  onStage?: (stage: 'PREPARING' | 'UPLOADING') => void;
  bizType?: ChunkUploadBizType;
  /** 单个文件同时上传的分片数，默认由 REACT_APP_UPLOAD_CONCURRENCY 控制。 */
  concurrency?: number;
  /** 单个分片失败后的重试次数，默认 3 次。 */
  maxRetries?: number;
}

export interface IArticleSavePayload {
  id?: string;
  title: string;
  summary?: string;
  content?: string;
  /** 图文正文或视频介绍的 Quill HTML；content 仍保留纯文本用于摘要、搜索和审核。 */
  contentHtml?: string;
  contentParagraphs?: Record<string, string>;
  coverUrl?: string | null;
  videoUrl?: string | null;
  imageUrls?: string[];
  categoryId?: number;
  categoryIds?: number[];
  postType: PostType;
  /** 转发引用原帖 */
  refArticleId?: string;
  /** 关联 Steam 游戏 appId 列表 */
  gameAppIds?: number[];
  /** 0 草稿；其它值均会进入待审核 */
  status: number;
}

export interface IArticleItem {
  id: number;
  publicId: string;
  authorAccountId: number;
  title: string;
  summary?: string;
  coverUrl?: string;
  videoUrl?: string;
  postType?: number;
  refArticleId?: string;
  categoryId?: number;
  categoryIds?: number[];
  status: number;
  auditMessage?: string;
  publishedTime?: string;
  createTime?: string;
  updateTime?: string;
  actionTime?: string;
  gameTags?: Array<{
    appId: number;
    name: string;
    headerImage?: string;
  }>;
}

export interface IArticleProgress {
  articleId: string;
  status: number;
  auditMessage?: string;
  postType?: number;
  taskStatus?: number | null;
  taskErrorMessage?: string | null;
  auditStage?: number | null;
  auditStageText?: string;
  uploadPercent?: number | null;
  uploadStatus?: string | null;
  activeUploadIds?: string[];
}

/**
 * 文章对外只使用 publicId。纯数字 ID 是数据库主键，不能进入作者侧请求链路。
 */
export function assertArticlePublicId(value: unknown): string {
  if (typeof value !== 'string') {
    throw new Error('文章 ID 协议异常：保存接口未返回公开 ID');
  }

  const publicId = value.trim();
  if (!publicId || /^\d+$/.test(publicId)) {
    throw new Error('文章 ID 协议异常：禁止使用数据库内部 ID');
  }

  return publicId;
}

/**
 * 进度接口的状态字段来自后端 JSON，统一在请求层收敛成前端使用的数值。
 * 这样即使网关/旧服务版本把数字序列化成字符串，也不会把已发布状态误判成审核中。
 */
function normalizeArticleProgress(data: IArticleProgress): IArticleProgress {
  const status = Number(data.status);
  const taskStatus =
    data.taskStatus == null ? data.taskStatus : Number(data.taskStatus);

  return {
    ...data,
    articleId: String(data.articleId),
    status: Number.isFinite(status) ? status : ARTICLE_STATUS.PENDING,
    taskStatus:
      taskStatus == null || Number.isFinite(taskStatus) ? taskStatus : null,
  };
}

function getUploadErrorCode(error: unknown): number | undefined {
  if (!error || typeof error !== 'object') return undefined;
  const value = error as {
    code?: unknown;
    response?: { status?: unknown };
  };
  if (typeof value.code === 'number') return value.code;
  if (typeof value.response?.status === 'number') return value.response.status;
  return undefined;
}

/** 判断分片错误是否可能由网络/服务端瞬态故障引起。 */
function isRetryableUploadError(error: unknown): boolean {
  const code = getUploadErrorCode(error);
  if (code == null) return true;
  return code === 408 || code === 425 || code === 429 || code >= 500;
}

function waitForUploadRetry(
  delayMs: number,
  signal?: AbortSignal,
): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) {
      reject(new DOMException('Upload aborted', 'AbortError'));
      return;
    }
    const timer = window.setTimeout(() => {
      signal?.removeEventListener('abort', onAbort);
      resolve();
    }, delayMs);
    const onAbort = () => {
      window.clearTimeout(timer);
      signal?.removeEventListener('abort', onAbort);
      reject(new DOMException('Upload aborted', 'AbortError'));
    };
    signal?.addEventListener('abort', onAbort, { once: true });
  });
}

async function uploadChunkWithRetry(
  uploadId: string,
  chunkIndex: number,
  chunk: Blob,
  signal: AbortSignal | undefined,
  maxRetries: number,
  onProgress?: (loaded: number, total?: number) => void,
): Promise<void> {
  for (let attempt = 0; ; attempt += 1) {
    if (signal?.aborted) {
      throw new DOMException('Upload aborted', 'AbortError');
    }
    // 失败重试会重新发送整个分片，避免把上一次请求已经发送的字节重复计入。
    onProgress?.(0, chunk.size);
    try {
      await uploadChunkApi(uploadId, chunkIndex, chunk, signal, onProgress);
      return;
    } catch (error) {
      if (
        attempt >= maxRetries ||
        !isRetryableUploadError(error) ||
        signal?.aborted
      ) {
        throw error;
      }
      await waitForUploadRetry(
        Math.min(5000, UPLOAD_CHUNK_RETRY_BASE_DELAY_MS * 2 ** attempt),
        signal,
      );
    }
  }
}

export function listCategoriesApi(page = 1, size = 50) {
  return hyRequest.get<IDataType<ICategory[]>>({
    url: '/category/list',
    params: { page, size },
  });
}

export function uploadImagesApi(files: File[]) {
  const formData = new FormData();
  files.forEach((file) => formData.append('files', file));
  return hyRequest.post<IDataType<IMediaUpload[]>>({
    url: '/file/upload',
    data: formData,
    timeout: 60_000,
  });
}

export function initChunkUploadApi(payload: {
  fileName: string;
  fileSize: number;
  fileMd5?: string;
  contentType?: string;
  bizType?: string;
  articleId?: number;
}) {
  return hyRequest.post<IDataType<IChunkUploadInit>>({
    url: '/file/upload/init',
    data: payload,
  });
}

export function uploadChunkApi(
  uploadId: string,
  chunkIndex: number,
  chunk: Blob,
  signal?: AbortSignal,
  onProgress?: (loaded: number, total?: number) => void,
) {
  const formData = new FormData();
  formData.append('uploadId', uploadId);
  formData.append('chunkIndex', String(chunkIndex));
  formData.append('file', chunk, `chunk-${chunkIndex}`);
  return hyRequest.post<IDataType<IChunkUploadStatus>>({
    url: '/file/upload/chunk',
    data: formData,
    timeout: UPLOAD_CHUNK_REQUEST_TIMEOUT_MS,
    signal,
    onUploadProgress: onProgress
      ? (event) => onProgress(event.loaded, event.total)
      : undefined,
  });
}

export function mergeChunkUploadApi(uploadId: string) {
  return hyRequest.post<IDataType<IMediaUpload>>({
    url: '/file/upload/merge',
    data: { uploadId },
    timeout: 120_000,
  });
}

export function abortChunkUploadApi(uploadId: string) {
  return hyRequest.post<IDataType<null>>({
    url: '/file/upload/abort',
    data: { uploadId },
  });
}

export function bindChunkUploadApi(uploadId: string, publicId: string) {
  return hyRequest.post<IDataType<null>>({
    url: '/file/upload/bind',
    data: { uploadId, articleId: assertArticlePublicId(publicId) },
  });
}

export function getChunkUploadStatusApi(uploadId: string) {
  return hyRequest.get<IDataType<IChunkUploadStatus>>({
    url: `/file/upload/${uploadId}/status`,
  });
}

export function saveArticleApi(payload: IArticleSavePayload) {
  const { id: publicId, ...body } = payload;
  const request = publicId
    ? hyRequest.put<IDataType<string>>({
        url: `/article/${publicId}`,
        data: body,
      })
    : hyRequest.post<IDataType<string>>({
        url: '/article',
        data: body,
      });

  return request.then((res) => ({
    ...res,
    data: assertArticlePublicId(res.data),
  }));
}

export function submitArticleAuditApi(id: string) {
  return hyRequest.put<IDataType<null>>({
    url: `/article/${id}/publish`,
  });
}

export function unpublishArticleApi(id: string) {
  return hyRequest.put<IDataType<null>>({
    url: `/article/${id}/unpublish`,
  });
}

export function deleteArticleApi(id: string) {
  return hyRequest.delete<IDataType<null>>({
    url: `/article/${id}`,
  });
}

export function getMyArticlesPageApi(
  page = 1,
  size = 20,
  tab?: PostSubTabKey | 'unpublished',
): Promise<IPageResult<IArticleItem>> {
  return hyRequest.get<IPageResult<IArticleItem>>({
    url: '/article/my',
    params: { page, size, tab },
  });
}

/** 个人页帖子 Tab：直接使用内容服务按 status 返回的分页结果。 */
export async function getMyArticlesPageForProfileTab(
  page = 1,
  size = 20,
  tab: PostSubTabKey,
): Promise<IPageResult<IArticleItem>> {
  return getMyArticlesPageApi(page, size, tab);
}

/** 作者已发布帖子（他人主页，按 accountId） */
export async function getAuthorPublishedArticlesByAccountApi(
  accountId: number,
  page = 1,
  size = 20,
): Promise<IPageResult<IArticleItem>> {
  const limit = page * size;
  const res = await hyRequest.get<IDataType<IArticleItem[]>>({
    url: `/article/author/account/${accountId}/published`,
    params: { size: limit },
  });
  const all = res.data || [];
  const start = (page - 1) * size;
  const data = all.slice(start, start + size);
  const total = all.length < limit ? all.length : limit + 1;

  return {
    code: 200,
    message: 'success',
    data,
    page,
    size,
    total,
  };
}

export function getArticleDetailApi(id: string, moderationPreview = false) {
  return hyRequest.get<
    IDataType<
      IArticleItem & {
        username?: string;
        avatar?: string;
        content?: string;
        contentHtml?: string;
        contentParagraphs?: Record<string, string>;
        imageUrls?: string[];
        imageRefs?: string[];
        coverRef?: string;
        videoRef?: string;
        refArticleId?: string;
        gameTags?: IArticleEditDetail['gameTags'];
        refArticle?: {
          id: string;
          title: string;
          summary?: string;
          coverUrl?: string;
          videoUrl?: string;
          postType?: number;
          accountId?: number;
          username?: string;
          avatar?: string;
        };
      }
    >
  >({
    url: moderationPreview
      ? `/article/${id}/moderation-preview`
      : `/article/${id}`,
  });
}

export type IArticleEditDetail = IArticleItem & {
  username?: string;
  avatar?: string;
  content?: string;
  contentHtml?: string;
  contentParagraphs?: Record<string, string>;
  imageUrls?: string[];
  /** 作者编辑非发布内容时的持久化媒体引用；预览地址在 imageUrls/coverUrl 中。 */
  imageRefs?: string[];
  coverRef?: string;
  videoRef?: string;
  refArticleId?: string;
  gameTags?: Array<{
    appId: number;
    name: string;
    headerImage?: string;
  }>;
  refArticle?: {
    id: string;
    title: string;
    summary?: string;
    coverUrl?: string;
    videoUrl?: string;
    postType?: number;
    accountId?: number;
    username?: string;
    avatar?: string;
  };
};

/** 作者读取自己的文章（任意状态，用于编辑） */
export function getMyArticleDetailApi(id: string) {
  return hyRequest.get<IDataType<IArticleEditDetail>>({
    url: `/article/${id}/mine`,
  });
}

/** 编辑页加载当前用户自己的帖子，服务端统一返回新数据格式。 */
export async function loadArticleForEditApi(
  id: string,
): Promise<IDataType<IArticleEditDetail>> {
  return getMyArticleDetailApi(id);
}

export function getLatestArticlesApi(categoryId?: number, size = 10) {
  return hyRequest.get<IDataType<IArticleItem[]>>({
    url: '/article/latest',
    params: { categoryId, size },
  });
}

export function getMoreArticlesApi(
  lastId: string,
  categoryId?: number,
  size = 20,
) {
  return hyRequest.get<IDataType<IArticleItem[]>>({
    url: '/article/more',
    params: { lastId, categoryId, size },
  });
}

/** 按 id 批量拉列表字段（含 gameTags，与首页 latest 一致） */
export function listArticlesByIdsApi(ids: string[]) {
  return hyRequest.post<IDataType<IArticleItem[]>>({
    url: '/article/listByIds',
    data: ids,
  });
}

export function getArticleProgressApi(id: string) {
  return hyRequest
    .get<IDataType<IArticleProgress>>({
      url: `/article/${id}/progress`,
    })
    .then((res) => ({ ...res, data: normalizeArticleProgress(res.data) }));
}

export function articleStatusLabel(status: number): string {
  switch (status) {
    case ARTICLE_STATUS.DRAFT:
      return '草稿';
    case ARTICLE_STATUS.PUBLISHED:
      return '已发布';
    case ARTICLE_STATUS.PENDING:
      return '审核中';
    case ARTICLE_STATUS.OFFLINE:
      return '已取消上架';
    case ARTICLE_STATUS.REJECTED:
      return '已驳回';
    default:
      return '未知';
  }
}

/**
 * 文件分片上传：浏览器并发发送多个单分片请求，由 content-service 的独立线程池受控写入 MinIO。
 * 支持按文件 MD5 复用服务端会话，因此网络失败后重新保存可以继续上传已完成分片。
 */
export async function uploadFileWithProgress(
  file: File,
  onProgress: (percent: number) => void,
  options?: IChunkUploadOptions,
): Promise<IMediaUpload> {
  options?.onStage?.('PREPARING');
  const fileMd5 = await calculateFileMd5(file, options?.signal);
  if (options?.signal?.aborted) {
    throw new DOMException('Upload aborted', 'AbortError');
  }
  const initRes = await initChunkUploadApi({
    fileName: file.name,
    fileSize: file.size,
    fileMd5,
    contentType: file.type || 'application/octet-stream',
    bizType: options?.bizType || 'video',
    articleId: options?.articleId,
  });
  const { uploadId, chunkSize, totalChunks, uploadedChunks } = initRes.data;
  options?.onUploadId?.(uploadId);
  if (initRes.data.instant) {
    onProgress(100);
    return {
      objectKey: initRes.data.objectKey || '',
      pendingUrl: initRes.data.pendingUrl || '',
      previewUrl: initRes.data.previewUrl || '',
    };
  }
  options?.onStage?.('UPLOADING');
  const done = new Set(uploadedChunks || []);
  const getChunkByteLength = (chunkIndex: number) => {
    const start = chunkIndex * chunkSize;
    return Math.max(0, Math.min(file.size, start + chunkSize) - start);
  };
  let uploadedBytes = Array.from(done).reduce(
    (sum, chunkIndex) => sum + getChunkByteLength(chunkIndex),
    0,
  );
  const inFlightBytes = new Map<number, number>();
  const reportProgress = () => {
    const inFlightUploadedBytes = Array.from(inFlightBytes.values()).reduce(
      (sum, bytes) => sum + bytes,
      0,
    );
    const percent = file.size
      ? ((uploadedBytes + inFlightUploadedBytes) / file.size) * 100
      : 100;
    onProgress(Math.min(100, Number(percent.toFixed(1))));
  };
  reportProgress();
  const pendingChunks = Array.from(
    { length: totalChunks },
    (_, index) => index,
  ).filter((index) => !done.has(index));
  const concurrency = Math.min(
    Math.max(1, Math.floor(options?.concurrency ?? UPLOAD_CHUNK_CONCURRENCY)),
    pendingChunks.length || 1,
  );
  const maxRetries = Math.max(
    0,
    Math.floor(options?.maxRetries ?? UPLOAD_CHUNK_MAX_RETRIES),
  );
  let nextChunkIndex = 0;
  let firstError: unknown;
  let abortPromise: Promise<void> | null = null;

  const abortRemoteUpload = (): Promise<void> => {
    if (!abortPromise) {
      abortPromise = abortChunkUploadApi(uploadId).then(
        () => undefined,
        () => undefined,
      );
    }
    return abortPromise;
  };

  const uploadWorker = async (): Promise<void> => {
    while (firstError === undefined) {
      if (options?.signal?.aborted) {
        firstError = new DOMException('Upload aborted', 'AbortError');
        return;
      }
      const position = nextChunkIndex;
      nextChunkIndex += 1;
      const chunkIndex = pendingChunks[position];
      if (chunkIndex === undefined) return;

      const start = chunkIndex * chunkSize;
      const end = Math.min(file.size, start + chunkSize);
      const currentChunkBytes = end - start;
      try {
        await uploadChunkWithRetry(
          uploadId,
          chunkIndex,
          file.slice(start, end),
          options?.signal,
          maxRetries,
          (loaded, total) => {
            const progressTotal =
              total && total > 0 ? total : currentChunkBytes;
            const normalizedLoaded = Math.min(
              currentChunkBytes,
              Math.max(0, (loaded * currentChunkBytes) / progressTotal),
            );
            inFlightBytes.set(chunkIndex, normalizedLoaded);
            reportProgress();
          },
        );
        inFlightBytes.delete(chunkIndex);
        uploadedBytes += currentChunkBytes;
        done.add(chunkIndex);
        reportProgress();
      } catch (error) {
        inFlightBytes.delete(chunkIndex);
        if (firstError === undefined) {
          firstError = options?.signal?.aborted
            ? new DOMException('Upload aborted', 'AbortError')
            : error;
        }
      }
    }
  };

  await Promise.all(Array.from({ length: concurrency }, () => uploadWorker()));
  if (firstError !== undefined) {
    if (options?.signal?.aborted) await abortRemoteUpload();
    throw firstError;
  }

  if (options?.signal?.aborted) {
    await abortRemoteUpload();
    throw new DOMException('Upload aborted', 'AbortError');
  }
  const merged = await mergeChunkUploadApi(uploadId);
  onProgress(100);
  return merged.data;
}

/** 视频分片上传：保留原有调用入口。 */
export function uploadVideoWithProgress(
  file: File,
  onProgress: (percent: number) => void,
  options?: Omit<IChunkUploadOptions, 'bizType'>,
): Promise<IMediaUpload> {
  return uploadFileWithProgress(file, onProgress, {
    ...options,
    bizType: 'video',
  });
}
