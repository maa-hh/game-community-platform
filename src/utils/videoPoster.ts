const posterCache = new Map<string, string>();

const POSTER_MAX_WIDTH = 1280;

/** 从视频元素当前画面生成压缩后的 JPEG，供视频封面上传使用。 */
export function captureVideoFrameFromElement(
  video: HTMLVideoElement,
): Promise<Blob | undefined> {
  const width = video.videoWidth;
  const height = video.videoHeight;
  if (!width || !height) return Promise.resolve(undefined);

  const scale = Math.min(1, POSTER_MAX_WIDTH / width);
  const canvas = document.createElement('canvas');
  canvas.width = Math.max(1, Math.round(width * scale));
  canvas.height = Math.max(1, Math.round(height * scale));
  const context = canvas.getContext('2d');
  if (!context) return Promise.resolve(undefined);
  context.drawImage(video, 0, 0, canvas.width, canvas.height);

  return new Promise((resolve) => {
    const handleBlob: BlobCallback = (blob) => resolve(blob || undefined);
    canvas.toBlob(handleBlob, 'image/jpeg', 0.86);
  });
}

/** 从本地视频文件指定时间截取一帧，不请求公网地址。 */
export function captureVideoFrame(
  file: File,
  timeSeconds = 1,
): Promise<Blob | undefined> {
  const sourceUrl = URL.createObjectURL(file);

  return captureVideoFrameFromSource(sourceUrl, timeSeconds, true, false);
}

/** 从已保存的视频地址指定时间截取一帧，需要资源服务器允许跨域读取。 */
export function captureVideoFrameFromUrl(
  videoUrl: string,
  timeSeconds = 1,
): Promise<Blob | undefined> {
  return captureVideoFrameFromSource(videoUrl, timeSeconds, false, true);
}

/** 统一处理本地 Blob URL 与跨域视频地址的取帧和资源清理。 */
function captureVideoFrameFromSource(
  sourceUrl: string,
  timeSeconds: number,
  revokeSourceUrl: boolean,
  crossOrigin: boolean,
): Promise<Blob | undefined> {
  return new Promise((resolve) => {
    const video = document.createElement('video');
    let settled = false;
    const timeoutId = window.setTimeout(() => finish(), 15000);

    const finish = (result?: Blob) => {
      if (settled) return;
      settled = true;
      window.clearTimeout(timeoutId);
      video.pause();
      video.removeAttribute('src');
      video.load();
      if (revokeSourceUrl) URL.revokeObjectURL(sourceUrl);
      resolve(result);
    };

    const drawFrame = () => {
      void captureVideoFrameFromElement(video).then(finish);
    };

    video.muted = true;
    video.playsInline = true;
    video.preload = 'auto';
    if (crossOrigin) video.crossOrigin = 'anonymous';
    video.addEventListener('error', () => finish(), { once: true });
    video.addEventListener(
      'loadedmetadata',
      () => {
        const duration = Number.isFinite(video.duration) ? video.duration : 0;
        const target = Math.min(
          Math.max(0, timeSeconds),
          Math.max(0, duration - 0.05),
        );
        if (target <= 0) {
          video.addEventListener('loadeddata', drawFrame, { once: true });
          return;
        }
        video.addEventListener('seeked', drawFrame, { once: true });
        try {
          video.currentTime = target;
        } catch {
          finish();
        }
      },
      { once: true },
    );
    video.src = sourceUrl;
    video.load();
  });
}

/** 从视频 URL 截取首帧作为封面（带内存缓存） */
export function captureVideoPoster(
  videoUrl: string,
): Promise<string | undefined> {
  const cached = posterCache.get(videoUrl);
  if (cached) return Promise.resolve(cached);

  return new Promise((resolve) => {
    const video = document.createElement('video');
    video.crossOrigin = 'anonymous';
    video.muted = true;
    video.playsInline = true;
    video.preload = 'auto';
    video.src = videoUrl;

    let settled = false;
    const finish = (result?: string) => {
      if (settled) return;
      settled = true;
      video.pause();
      video.removeAttribute('src');
      video.load();
      if (result) posterCache.set(videoUrl, result);
      resolve(result);
    };

    const drawFrame = () => {
      try {
        const width = video.videoWidth;
        const height = video.videoHeight;
        if (!width || !height) {
          finish();
          return;
        }
        const canvas = document.createElement('canvas');
        canvas.width = width;
        canvas.height = height;
        const ctx = canvas.getContext('2d');
        if (!ctx) {
          finish();
          return;
        }
        ctx.drawImage(video, 0, 0, width, height);
        finish(canvas.toDataURL('image/jpeg', 0.82));
      } catch {
        finish();
      }
    };

    video.addEventListener('error', () => finish(), { once: true });
    video.addEventListener(
      'loadeddata',
      () => {
        const seekAndDraw = () => {
          video.addEventListener('seeked', drawFrame, { once: true });
          try {
            video.currentTime = Math.min(0.1, video.duration || 0.1);
          } catch {
            drawFrame();
          }
        };
        seekAndDraw();
      },
      { once: true },
    );
  });
}
