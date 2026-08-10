import React, { memo } from 'react';
import type { FC } from 'react';
import { LinkOutlined, RetweetOutlined, ShopOutlined } from '@ant-design/icons';

import type { GameShareActionItem } from '../types';

interface GameShareActionsProps {
  hasSteamUrl: boolean;
  onRepost: () => void;
  onCopySteam: () => void;
  onCopyDetail: () => void;
}

const GameShareActions: FC<GameShareActionsProps> = ({
  hasSteamUrl,
  onRepost,
  onCopySteam,
  onCopyDetail,
}) => {
  const items: GameShareActionItem[] = [
    {
      key: 'repost',
      icon: <RetweetOutlined />,
      label: '分享为动态',
      desc: '可自定义标题与正文',
      onClick: onRepost,
    },
    {
      key: 'steam',
      icon: <ShopOutlined />,
      label: '复制 Steam 链接',
      desc: '商店页面地址',
      disabled: !hasSteamUrl,
      onClick: onCopySteam,
    },
    {
      key: 'detail',
      icon: <LinkOutlined />,
      label: '复制游戏详情链接',
      desc: '本站游戏页地址',
      onClick: onCopyDetail,
    },
  ];

  return (
    <>
      {items.map((item) => (
        <button
          key={item.key}
          type="button"
          className="share-sheet__item"
          disabled={item.disabled}
          onClick={item.onClick}
        >
          {item.icon}
          <span>{item.label}</span>
          <span className="share-sheet__desc">{item.desc}</span>
        </button>
      ))}
    </>
  );
};

export default memo(GameShareActions);
