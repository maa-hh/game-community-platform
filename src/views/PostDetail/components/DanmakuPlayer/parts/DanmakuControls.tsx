import React, { memo } from 'react';
import type { FC, KeyboardEvent } from 'react';
import { Button, Input, Select, Space, Switch, Tooltip } from 'antd';
import { SendOutlined, SettingOutlined } from '@ant-design/icons';

import type { DanmakuDensity } from '../types';

interface DanmakuControlsProps {
  enabled: boolean;
  density: DanmakuDensity;
  speed: number;
  draft: string;
  onToggle: (enabled: boolean) => void;
  onDensityChange: (density: DanmakuDensity) => void;
  onSpeedChange: (speed: number) => void;
  onDraftChange: (value: string) => void;
  onSend: () => void;
}

const DanmakuControls: FC<DanmakuControlsProps> = ({
  enabled,
  density,
  speed,
  draft,
  onToggle,
  onDensityChange,
  onSpeedChange,
  onDraftChange,
  onSend,
}) => {
  const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter') onSend();
  };

  return (
    <div className="danmaku-player__controls">
      <Space size={10} wrap>
        <Space size={6}>
          <Switch checked={enabled} onChange={onToggle} size="small" />
          <span>弹幕</span>
        </Space>
        <Tooltip title="弹幕密度">
          <SettingOutlined />
        </Tooltip>
        <Select
          size="small"
          value={density}
          onChange={onDensityChange}
          options={[
            { value: 'low', label: '低密度' },
            { value: 'medium', label: '标准' },
            { value: 'high', label: '高密度' },
          ]}
        />
        <Select
          size="small"
          value={speed}
          onChange={onSpeedChange}
          options={[0.75, 1, 1.25, 1.5, 2].map((item) => ({
            value: item,
            label: `${item}x`,
          }))}
        />
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
          type="primary"
          icon={<SendOutlined />}
          onClick={onSend}
        >
          发送
        </Button>
      </Space.Compact>
    </div>
  );
};

export default memo(DanmakuControls);
