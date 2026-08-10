import React, { memo } from 'react';
import type { FC } from 'react';
import { LinkOutlined, RetweetOutlined } from '@ant-design/icons';

import type { ShareActionItem } from '../types';

interface ShareActionsProps {
  onCopy: () => void;
  onRepost: () => void;
}

const ShareActions: FC<ShareActionsProps> = ({ onCopy, onRepost }) => {
  const items: ShareActionItem[] = [
    {
      key: 'copy',
      icon: <LinkOutlined />,
      label: '复制链接',
      desc: '含标题与引导语',
      onClick: onCopy,
    },
    {
      key: 'repost',
      icon: <RetweetOutlined />,
      label: '转发动态',
      desc: '可自定义标题与正文',
      onClick: onRepost,
    },
  ];

  return (
    <>
      {items.map((item) => (
        <button
          key={item.key}
          type="button"
          className="share-sheet__item"
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

export default memo(ShareActions);
