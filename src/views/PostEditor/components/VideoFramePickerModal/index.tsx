import { useEffect, useRef, useState } from 'react';
import { Modal, Slider, Typography, message } from 'antd';

import { captureVideoFrameFromElement } from '@/utils/videoPoster';

import './style.less';

export interface IVideoFramePickerModalProps {
  open: boolean;
  file: File | null;
  videoUrl?: string | null;
  onCancel: () => void;
  onConfirm: (file: File) => void;
}

/** 在本地视频时间轴上选择画面并导出为封面文件。 */
export default function VideoFramePickerModal({
  open,
  file,
  videoUrl,
  onCancel,
  onConfirm,
}: IVideoFramePickerModalProps) {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const [sourceUrl, setSourceUrl] = useState('');
  const [duration, setDuration] = useState(0);
  const [currentTime, setCurrentTime] = useState(0);
  const [confirmLoading, setConfirmLoading] = useState(false);

  useEffect(() => {
    if (!open || (!file && !videoUrl)) {
      setSourceUrl('');
      setDuration(0);
      setCurrentTime(0);
      return undefined;
    }

    const localUrl = file ? URL.createObjectURL(file) : null;
    const url = localUrl || videoUrl || '';
    setSourceUrl(url);
    setDuration(0);
    setCurrentTime(0);
    return () => {
      if (localUrl) URL.revokeObjectURL(localUrl);
    };
  }, [file, open, videoUrl]);

  const handleLoadedMetadata = () => {
    const video = videoRef.current;
    if (!video) return;
    setDuration(Number.isFinite(video.duration) ? video.duration : 0);
  };

  const handleTimeUpdate = () => {
    const video = videoRef.current;
    if (video) setCurrentTime(video.currentTime);
  };

  const handleSliderChange = (value: number) => {
    const video = videoRef.current;
    if (!video) return;
    video.currentTime = value;
    setCurrentTime(value);
  };

  const handleConfirm = async () => {
    const video = videoRef.current;
    if (!video) return;
    setConfirmLoading(true);
    try {
      if (Math.abs(video.currentTime - currentTime) > 0.05) {
        await new Promise<void>((resolve) => {
          video.addEventListener('seeked', () => resolve(), { once: true });
          video.currentTime = currentTime;
        });
      }
      const blob = await captureVideoFrameFromElement(video);
      if (!blob) {
        message.error('当前画面暂时无法生成封面');
        return;
      }
      onConfirm(
        new File([blob], `video-cover-${Date.now()}.jpg`, {
          type: 'image/jpeg',
        }),
      );
    } finally {
      setConfirmLoading(false);
    }
  };

  return (
    <Modal
      title="从视频中选择封面"
      open={open}
      centered
      width={680}
      destroyOnHidden
      okText="使用此画面"
      cancelText="取消"
      confirmLoading={confirmLoading}
      okButtonProps={{ disabled: !sourceUrl || duration <= 0 }}
      onCancel={onCancel}
      onOk={() => void handleConfirm()}
    >
      <div className="video-frame-picker">
        {sourceUrl ? (
          <video
            ref={videoRef}
            className="video-frame-picker__video"
            src={sourceUrl}
            crossOrigin={file ? undefined : 'anonymous'}
            controls
            playsInline
            preload="metadata"
            onLoadedMetadata={handleLoadedMetadata}
            onTimeUpdate={handleTimeUpdate}
          />
        ) : null}
        <Typography.Text type="secondary">
          拖动时间轴选择画面，当前时间 {currentTime.toFixed(1)} 秒
        </Typography.Text>
        <Slider
          min={0}
          max={Math.max(duration, 0.1)}
          step={0.1}
          value={Math.min(currentTime, Math.max(duration, 0.1))}
          disabled={!sourceUrl || duration <= 0}
          tooltip={{ formatter: (value) => `${value?.toFixed(1)} 秒` }}
          onChange={(value) => handleSliderChange(value as number)}
        />
      </div>
    </Modal>
  );
}
