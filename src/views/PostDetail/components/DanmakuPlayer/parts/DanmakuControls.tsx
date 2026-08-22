import React, { memo } from 'react';
import type { FC, KeyboardEvent } from 'react';
import { Button, Flex, Input, Popover, Select, Space, Switch } from 'antd';
import { CommentOutlined, SendOutlined } from '@ant-design/icons';

import type { DanmakuDensity } from '../types';

interface DanmakuControlsProps {
  enabled: boolean;
  draft: string;
  onToggle: (enabled: boolean) => void;
  onDraftChange: (value: string) => void;
  onSend: () => void;
}

interface DanmakuSettingsProps {
  density: DanmakuDensity;
  speed: number;
  onDensityChange: (density: DanmakuDensity) => void;
  onSpeedChange: (speed: number) => void;
}

const getPlayerPopupContainer = (triggerNode: HTMLElement) =>
  triggerNode.closest<HTMLElement>('.video-player__dplayer') ?? document.body;

const DanmakuControls: FC<DanmakuControlsProps> = ({
  enabled,
  draft,
  onToggle,
  onDraftChange,
  onSend,
}) => {
  const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter') onSend();
  };

  return (
    <div className="danmaku-player__controls">
      <Space size={6} className="danmaku-player__toggle">
        <Switch checked={enabled} onChange={onToggle} size="small" />
        <span>弹幕</span>
      </Space>
      <Space.Compact className="danmaku-player__composer">
        <Input
          size="small"
          value={draft}
          maxLength={200}
          placeholder="发一条弹幕…"
          onChange={(event) => onDraftChange(event.target.value)}
          onKeyDown={handleKeyDown}
        />
        <Button
          size="small"
          icon={<SendOutlined />}
          className="video-player__control-trigger danmaku-player__send-trigger"
          onClick={onSend}
          aria-label="发送弹幕"
        />
      </Space.Compact>
    </div>
  );
};

export const DanmakuSettings: FC<DanmakuSettingsProps> = memo(
  ({ density, speed, onDensityChange, onSpeedChange }) => (
    <Popover
      trigger="click"
      placement="topRight"
      rootClassName="danmaku-player__settings-popover"
      classNames={{ root: 'danmaku-player__settings-popover' }}
      getPopupContainer={getPlayerPopupContainer}
      content={
        <Flex vertical gap={8}>
          <Space size={8}>
            <span>弹幕速度</span>
            <Select
              size="small"
              value={speed}
              onChange={onSpeedChange}
              getPopupContainer={getPlayerPopupContainer}
              popupMatchSelectWidth={96}
              classNames={{
                popup: { root: 'danmaku-player__settings-select-popup' },
              }}
              options={[0.5, 0.75, 1, 1.25, 1.5, 1.75, 2].map((item) => ({
                value: item,
                label: `${item}x`,
              }))}
            />
          </Space>
          <Space size={8}>
            <span>弹幕密度</span>
            <Select
              size="small"
              value={density}
              onChange={onDensityChange}
              getPopupContainer={getPlayerPopupContainer}
              popupMatchSelectWidth={96}
              classNames={{
                popup: { root: 'danmaku-player__settings-select-popup' },
              }}
              options={[
                { value: 'low', label: '低密度' },
                { value: 'medium', label: '标准' },
                { value: 'high', label: '高密度' },
              ]}
            />
          </Space>
        </Flex>
      }
    >
      <Button
        type="text"
        size="small"
        icon={<CommentOutlined />}
        className="video-player__control-trigger danmaku-player__settings-trigger"
        aria-label="弹幕设置"
      />
    </Popover>
  ),
);

export default memo(DanmakuControls);
