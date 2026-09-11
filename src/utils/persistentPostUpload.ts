import {
  ARTICLE_STATUS,
  bindChunkUploadApi,
  saveArticleApi,
  uploadFileWithProgress,
  type IArticleSavePayload,
  type IMediaUpload,
} from '@/service/content';

export type PersistentPostUploadStatus =
  | 'QUEUED'
  | 'PREPARING'
  | 'UPLOADING'
  | 'SAVING'
  | 'COMPLETED'
  | 'FAILED'
  | 'CANCELLED';

export interface PersistentPostUploadFile {
  kind: 'video' | 'image';
  file: File;
}

export interface PersistentPostUploadTask {
  id: string;
  accountId: number;
  payload: IArticleSavePayload;
  files: Array<{
    kind: 'video' | 'image';
    file: File;
  }>;
  uploadIds: string[];
  uploadedImages: IMediaUpload[];
  uploadedVideo?: IMediaUpload;
  articleId?: string;
  status: PersistentPostUploadStatus;
  progress: number;
  errorMessage?: string;
  updatedAt: number;
}

type PersistentPostUploadListener = (task: PersistentPostUploadTask) => void;

/**
 * 任务独立于编辑器组件存在，因此路由切换不会中断上传；任务仅存于当前
 * 浏览器会话，刷新或关闭浏览器后不会恢复。
 */
const runningTasks = new Map<string, Promise<PersistentPostUploadTask>>();
const taskControllers = new Map<string, AbortController>();
const discardedTaskIds = new Set<string>();
const taskRecords = new Map<string, PersistentPostUploadTask>();
const listeners = new Set<PersistentPostUploadListener>();

function createTaskId(): string {
  return `upload:${Date.now()}:${Math.random().toString(36).slice(2)}`;
}

function deleteTaskRecord(taskId: string): void {
  taskRecords.delete(taskId);
}

function getTask(taskId: string): PersistentPostUploadTask | undefined {
  return taskRecords.get(taskId);
}

function notify(task: PersistentPostUploadTask): void {
  listeners.forEach((listener) => listener(task));
}

function updateTask(
  task: PersistentPostUploadTask,
  patch: Partial<PersistentPostUploadTask>,
): PersistentPostUploadTask {
  const nextTask = {
    ...task,
    ...patch,
    updatedAt: Date.now(),
  };
  if (discardedTaskIds.has(task.id)) return nextTask;
  taskRecords.set(task.id, nextTask);
  notify(nextTask);
  return nextTask;
}

function mergeImageUrls(
  payload: IArticleSavePayload,
  uploadedImages: IMediaUpload[],
): IArticleSavePayload {
  const imageUrls = [
    ...(payload.imageUrls || []),
    ...uploadedImages.map((item) => item.pendingUrl),
  ];
  return {
    ...payload,
    imageUrls,
    coverUrl: payload.coverUrl || imageUrls[0] || null,
  };
}

async function uploadStoredFile(
  task: PersistentPostUploadTask,
  storedFile: PersistentPostUploadTask['files'][number],
  totalBytes: number,
  completedBytesBeforeCurrent: number,
  signal: AbortSignal,
): Promise<{ task: PersistentPostUploadTask; media: IMediaUpload }> {
  let currentTask = task;
  const media = await uploadFileWithProgress(
    storedFile.file,
    (percent) => {
      const uploadedBytes =
        completedBytesBeforeCurrent + (storedFile.file.size * percent) / 100;
      const progress = totalBytes
        ? Number(Math.min(100, (uploadedBytes / totalBytes) * 100).toFixed(1))
        : 100;
      currentTask = updateTask(currentTask, { progress });
    },
    {
      bizType: storedFile.kind,
      signal,
      onStage: (stage) => {
        currentTask = updateTask(currentTask, { status: stage });
      },
      onUploadId: (uploadId) => {
        if (currentTask.uploadIds.includes(uploadId)) return;
        currentTask = {
          ...currentTask,
          uploadIds: [...currentTask.uploadIds, uploadId],
          updatedAt: Date.now(),
        };
        taskRecords.set(currentTask.id, currentTask);
      },
    },
  );

  return { task: currentTask, media };
}

async function ensureArticleId(
  task: PersistentPostUploadTask,
): Promise<PersistentPostUploadTask> {
  if (task.articleId) return task;
  if (task.payload.id) {
    return updateTask(task, { articleId: task.payload.id });
  }

  // 先落一条草稿拿到稳定 publicId。当前会话内重试时只会 PUT
  // 同一篇文章，不会因为上传失败而重复创建帖子。
  const draftResult = await saveArticleApi({
    ...task.payload,
    status: ARTICLE_STATUS.DRAFT,
    videoUrl: null,
  });
  return updateTask(task, {
    articleId: draftResult.data,
    payload: { ...task.payload, id: draftResult.data },
  });
}

async function runTask(
  taskId: string,
  signal: AbortSignal,
): Promise<PersistentPostUploadTask> {
  const existing = await getTask(taskId);
  if (!existing) throw new Error('持久化上传任务不存在');

  let task = existing;
  task = await ensureArticleId(task);
  const totalFiles = task.files.length;
  const totalBytes = task.files.reduce((sum, item) => sum + item.file.size, 0);
  const completedFiles =
    task.uploadedImages.length + (task.uploadedVideo ? 1 : 0);
  let completed = completedFiles;
  let completedBytes = 0;
  let uploadedImageIndex = 0;

  task = await updateTask(task, {
    status: totalFiles > completed ? 'PREPARING' : 'SAVING',
    progress: totalBytes
      ? Number(Math.min(100, (completedBytes / totalBytes) * 100).toFixed(1))
      : 100,
    errorMessage: undefined,
  });

  for (const storedFile of task.files) {
    if (storedFile.kind === 'video' && task.uploadedVideo) {
      completedBytes += storedFile.file.size;
      continue;
    }
    if (
      storedFile.kind === 'image' &&
      uploadedImageIndex < task.uploadedImages.length
    ) {
      uploadedImageIndex += 1;
      completedBytes += storedFile.file.size;
      continue;
    }

    const uploadResult = await uploadStoredFile(
      task,
      storedFile,
      totalBytes,
      completedBytes,
      signal,
    );
    task = uploadResult.task;
    const media = uploadResult.media;

    completed += 1;
    completedBytes += storedFile.file.size;
    if (storedFile.kind === 'image') {
      uploadedImageIndex += 1;
    }
    task = await updateTask(task, {
      uploadedImages:
        storedFile.kind === 'image'
          ? [...task.uploadedImages, media]
          : task.uploadedImages,
      uploadedVideo: storedFile.kind === 'video' ? media : task.uploadedVideo,
      progress: totalBytes
        ? Number(Math.min(100, (completedBytes / totalBytes) * 100).toFixed(1))
        : 100,
    });
  }

  const payloadWithMedia = {
    ...mergeImageUrls(task.payload, task.uploadedImages),
    videoUrl: task.uploadedVideo?.pendingUrl || task.payload.videoUrl || null,
    id: task.articleId || task.payload.id,
  };
  task = await updateTask(task, {
    status: 'SAVING',
    progress: 100,
    payload: payloadWithMedia,
  });

  if (signal.aborted) {
    throw new DOMException('Upload aborted', 'AbortError');
  }
  const result = await saveArticleApi(payloadWithMedia);
  // 保存接口成功即代表文章和审核任务已经落库；绑定上传会话只是后续清理关联，
  // 不应让进度条在 100% 后继续等待多个串行请求才进入“排队审核中”。
  task = await updateTask(task, {
    articleId: result.data,
    status: 'QUEUED',
  });
  await Promise.all(
    task.uploadIds.map((uploadId) => bindChunkUploadApi(uploadId, result.data)),
  );

  return updateTask(task, {
    status: 'COMPLETED',
    progress: 100,
    errorMessage: undefined,
  });
}

function startTask(taskId: string): Promise<PersistentPostUploadTask> {
  const current = runningTasks.get(taskId);
  if (current) return current;

  const controller = new AbortController();
  taskControllers.set(taskId, controller);
  const promise = runTask(taskId, controller.signal)
    .then((task) => {
      if (task.status === 'COMPLETED') {
        // 完成后不再需要任务中的原始文件，避免连续上传长期占用内存。
        deleteTaskRecord(taskId);
      }
      return task;
    })
    .catch((error) => {
      const task = getTask(taskId);
      if (task) {
        updateTask(task, {
          status: 'FAILED',
          errorMessage:
            error instanceof Error ? error.message : '上传或保存失败',
        });
      }
      throw error;
    })
    .finally(() => {
      runningTasks.delete(taskId);
      taskControllers.delete(taskId);
    });
  runningTasks.set(taskId, promise);
  return promise;
}

export function subscribePersistentPostUploads(
  listener: PersistentPostUploadListener,
): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function startPersistentPostUpload(input: {
  accountId: number;
  payload: IArticleSavePayload;
  files: PersistentPostUploadFile[];
}): {
  taskId: string;
  promise: Promise<PersistentPostUploadTask>;
} {
  const taskId = createTaskId();
  const task: PersistentPostUploadTask = {
    id: taskId,
    accountId: input.accountId,
    payload: input.payload,
    files: input.files.map(({ kind, file }) => ({
      kind,
      file,
    })),
    uploadIds: [],
    uploadedImages: [],
    status: 'PREPARING',
    progress: 0,
    updatedAt: Date.now(),
  };

  taskRecords.set(task.id, task);
  notify(task);
  const promise = Promise.resolve().then(() => startTask(taskId));
  return { taskId, promise };
}

export async function getPersistentPostUploadPromise(
  taskId: string,
): Promise<PersistentPostUploadTask> {
  const task = await getTask(taskId);
  if (!task) throw new Error('持久化上传任务不存在');
  if (task.status === 'COMPLETED') return task;
  return startTask(taskId);
}

export function updatePersistentPostUploadPayload(
  taskId: string,
  payload: IArticleSavePayload,
): void {
  const task = getTask(taskId);
  if (!task || runningTasks.has(taskId)) return;

  const nextTask: PersistentPostUploadTask = {
    ...task,
    payload: {
      ...payload,
      id: task.articleId || payload.id,
    },
    status: 'PREPARING',
    errorMessage: undefined,
    updatedAt: Date.now(),
  };
  taskRecords.set(taskId, nextTask);
  notify(nextTask);
}

export function getPersistentPostUploadTask(
  taskId: string,
): PersistentPostUploadTask | undefined {
  return getTask(taskId);
}

export function discardPersistentPostUpload(taskId: string): void {
  const task = getTask(taskId);
  if (!task) return;
  discardedTaskIds.add(taskId);
  taskControllers.get(taskId)?.abort();
  deleteTaskRecord(taskId);
  notify({
    ...task,
    status: 'CANCELLED',
    updatedAt: Date.now(),
  });
}
