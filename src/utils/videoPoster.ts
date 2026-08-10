const posterCache = new Map<string, string>();

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
