import React, { memo, useState } from 'react';
import type { FC } from 'react';
import { message } from 'antd';

import VideoPlayer from '@/base-ui/VideoPlayer';
import { useRequireLogin } from '@/hooks/useRequireLogin';

import DanmakuControls from './parts/DanmakuControls';
import DanmakuOverlay from './parts/DanmakuOverlay';
import type { DanmakuPlayerProps } from './types';
import { useDanmaku } from './useDanmaku';

import './style.less';

const DanmakuPlayer: FC<DanmakuPlayerProps> = ({
  videoPublicId,
  url,
  pic,
  title,
  muted = true,
  onReport,
}) => {
  const { requireLogin } = useRequireLogin();
  const [draft, setDraft] = useState('');
  const danmaku = useDanmaku({
    videoPublicId,
    onSendError: (error) => message.error(error),
  });

  const send = () => {
    if (!requireLogin()) return;
    const content = draft.trim();
    if (!content || !danmaku.video) return;
    danmaku.send(content, Math.floor(danmaku.video.currentTime * 1000));
    setDraft('');
  };

  return (
    <div className="danmaku-player">
      <VideoPlayer
        url={url}
        pic={pic}
        title={title}
        autoplay
        muted={muted}
        loop
        mode="inline"
        onVideoReady={danmaku.setVideo}
        overlay={
          <DanmakuOverlay
            playheadMs={danmaku.playheadMs}
            messages={danmaku.messages}
            pendingMessages={danmaku.pendingMessages}
            enabled={danmaku.enabled}
            density={danmaku.density}
            speed={danmaku.speed}
            onReport={onReport}
          />
        }
      />
      <DanmakuControls
        enabled={danmaku.enabled}
        density={danmaku.density}
        speed={danmaku.speed}
        draft={draft}
        onToggle={danmaku.setEnabled}
        onDensityChange={danmaku.setDensity}
        onSpeedChange={danmaku.setSpeed}
        onDraftChange={setDraft}
        onSend={send}
      />
    </div>
  );
};

export default memo(DanmakuPlayer);
