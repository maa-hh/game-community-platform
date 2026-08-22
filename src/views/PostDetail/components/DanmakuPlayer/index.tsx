import React, { memo, useCallback, useEffect, useRef, useState } from 'react';
import type { FC } from 'react';
import { App } from 'antd';

import VideoPlayer from '@/base-ui/VideoPlayer';
import { useRequireLogin } from '@/hooks/useRequireLogin';
import { useAppSelector } from '@/store';

import DanmakuControls, { DanmakuSettings } from './parts/DanmakuControls';
import DanmakuOverlay from './parts/DanmakuOverlay';
import type { DanmakuPlayerProps } from './types';
import { useDanmaku } from './useDanmaku';

import './style.less';

const DanmakuPlayer: FC<DanmakuPlayerProps> = ({
  videoPublicId,
  targetDanmakuId,
  url,
  pic,
  title,
  muted = true,
  onReport,
  reportResetKey,
}) => {
  const { requireLogin } = useRequireLogin();
  const { message } = App.useApp();
  const accountId = useAppSelector((state) => state.auth.user?.accountId);
  const [draft, setDraft] = useState('');
  const danmaku = useDanmaku({
    videoPublicId,
    targetDanmakuId,
    accountId,
    onSendError: (error) => message.error(error),
  });
  const resumeAfterReportRef = useRef(false);
  const reportResetKeyRef = useRef(reportResetKey);

  const handleDanmakuReport = useCallback(
    (messageId: string) => {
      if (!onReport) return;
      const message = danmaku.messages.find((item) => item.id === messageId);
      if (message?.isMine) return;

      const video = danmaku.video;
      resumeAfterReportRef.current = Boolean(
        video && !video.paused && !video.ended,
      );
      video?.pause();
      onReport(messageId);
    },
    [danmaku.messages, danmaku.video, onReport],
  );

  useEffect(() => {
    if (
      reportResetKey == null ||
      reportResetKeyRef.current === reportResetKey
    ) {
      return;
    }
    reportResetKeyRef.current = reportResetKey;
    const shouldResume = resumeAfterReportRef.current;
    resumeAfterReportRef.current = false;
    const video = danmaku.video;
    if (!shouldResume || !video || video.ended || !video.paused) return;
    void video.play().catch(() => {
      // 浏览器阻止自动恢复播放时，保持用户当前的暂停状态。
    });
  }, [danmaku.video, reportResetKey]);

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
        controlContent={
          <DanmakuControls
            enabled={danmaku.enabled}
            draft={draft}
            onToggle={danmaku.setEnabled}
            onDraftChange={setDraft}
            onSend={send}
          />
        }
        controlTrailingContent={
          <DanmakuSettings
            density={danmaku.density}
            speed={danmaku.speed}
            onDensityChange={danmaku.setDensity}
            onSpeedChange={danmaku.setSpeed}
          />
        }
        overlay={
          <DanmakuOverlay
            playheadMs={danmaku.playheadMs}
            isPlaying={danmaku.isPlaying}
            timelineRevision={danmaku.timelineRevision}
            reportResetKey={reportResetKey}
            messages={danmaku.messages}
            pendingMessages={danmaku.pendingMessages}
            enabled={danmaku.enabled}
            density={danmaku.density}
            speed={danmaku.speed * danmaku.playbackRate}
            onReport={onReport ? handleDanmakuReport : undefined}
          />
        }
      />
    </div>
  );
};

export default memo(DanmakuPlayer);
