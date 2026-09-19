import React, { memo, useMemo } from 'react';
import type { FC } from 'react';
import { RetweetOutlined } from '@ant-design/icons';

import OverflowTagRow from '@/base-ui/OverflowTagRow';
import type { OverflowTagRowItem } from '@/base-ui/OverflowTagRow';
import type { ContentCardPostType, ContentCardTag } from '@/types/content';
import type { IGameTag } from '@/types/game';
import { resolvePostDisplayTags } from '@/utils/categoryTag';
import { preloadGameDetail } from '@/router/preload';

import './style.less';

export interface PostDisplayTagsProps {
  postType?: ContentCardPostType;
  tags?: ContentCardTag[];
  gameTags?: IGameTag[];
  className?: string;
}

/** 帖子分区/游戏/转发标签（与信息流卡片规则一致） */
const PostDisplayTags: FC<PostDisplayTagsProps> = ({
  postType,
  tags = [],
  gameTags = [],
  className,
}) => {
  const isRepost = postType === 'repost';
  const displayTags = useMemo(() => resolvePostDisplayTags(tags), [tags]);
  const tagItems = useMemo(() => {
    const items: OverflowTagRowItem[] = [];

    if (isRepost) {
      items.push({
        key: 'repost',
        text: '转发',
        variant: 'repost',
        leading: (
          <RetweetOutlined
            className="post-display-tags__repost-icon"
            aria-hidden
          />
        ),
      });
    }

    displayTags.forEach((tag) => {
      items.push({
        key: `category-${tag.text}-${tag.icon || ''}`,
        text: tag.text,
        icon: tag.icon,
      });
    });

    gameTags.forEach((tag) => {
      items.push({
        key: `game-${tag.appId}`,
        text: tag.name,
        icon: tag.iconUrl,
        variant: 'game',
        to: `/game/${tag.appId}`,
        onIntent: () => void preloadGameDetail(),
      });
    });

    return items;
  }, [displayTags, gameTags, isRepost]);

  if (tagItems.length === 0) return null;

  return (
    <OverflowTagRow
      className={`post-display-tags${className ? ` ${className}` : ''}`}
      preset="post"
      items={tagItems}
    />
  );
};

export default memo(PostDisplayTags);
