import { useEffect, useState } from 'react';

import { captureVideoPoster } from '@/utils/videoPoster';

/** 视频封面：优先 coverUrl，否则截取 videoUrl 首帧 */
export function useVideoPoster(
  videoUrl?: string,
  coverUrl?: string,
): { poster?: string; loading: boolean } {
  const [poster, setPoster] = useState<string | undefined>(coverUrl);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (coverUrl) {
      setPoster(coverUrl);
      setLoading(false);
      return undefined;
    }
    if (!videoUrl) {
      setPoster(undefined);
      setLoading(false);
      return undefined;
    }

    let cancelled = false;
    setLoading(true);
    captureVideoPoster(videoUrl)
      .then((url) => {
        if (!cancelled) setPoster(url);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [videoUrl, coverUrl]);

  return { poster, loading };
}
