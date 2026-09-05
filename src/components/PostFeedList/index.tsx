import React, { memo, useCallback, useMemo } from 'react';
import type { FC } from 'react';
import { Masonry } from 'masonic';
import type { RenderComponentProps } from 'masonic';

import ContentCard from '@/base-ui/ContentCard';
import FeedMasonryCard from '@/base-ui/FeedMasonryCard';
import HotRankRowCard from '@/base-ui/HotRankRowCard';
import FeedPanel from '@/components/FeedPanel';
import EmptyState from '@/components/EmptyState';
import { useUserDecorations } from '@/hooks/useUserDecorations';

import { sortFeedItemsByTime } from '@/utils/sortFeedItemsByTime';
import { sortHotRankItems } from '@/utils/sortHotRankItems';

import type { IPostFeedListProps } from './types';

import './style.less';

// MainLayout 会在进入详情时保留并隐藏列表页面。此处必须覆盖已加载卡片的
// 整个高度，不能让虚拟窗口在详情页滚到顶部后卸载原位置的卡片；否则回退时
// 路由恢复滚动位置与瀑布流补渲染会相隔一帧，形成可见跳动。
const KEEP_ALIVE_MASONRY_OVERSCAN = 100;

const PostFeedList: FC<IPostFeedListProps> = ({
  items,
  loading = false,
  emptyText = '暂无内容',
  onRefresh,
  onItemClick,
  onLikeClick,
  onFavoriteClick,
  infinite,
  showRank = false,
  layout = 'stack',
  panelClassName,
}) => {
  const authorIds = useMemo(
    () => items.map((item) => item.author.accountId),
    [items],
  );
  const isMasonry = layout === 'masonry';
  const isHotRank = layout === 'hotRank';
  const decorationAuthorIds = isMasonry || isHotRank ? [] : authorIds;
  const { get } = useUserDecorations(decorationAuthorIds);

  const listItems = useMemo(() => {
    if (isHotRank || (isMasonry && showRank)) {
      return sortHotRankItems(items);
    }
    if (isMasonry) return sortFeedItemsByTime(items);
    return items;
  }, [items, isHotRank, isMasonry, showRank]);

  const renderMasonryItem = useCallback(
    ({ data: item }: RenderComponentProps<(typeof items)[number]>) => (
      <FeedMasonryCard
        item={item}
        rank={showRank ? item.rank : undefined}
        hotScore={showRank ? item.hotScore : undefined}
        onClick={() => onItemClick(item.id)}
        onLikeClick={
          onLikeClick
            ? (event) => {
                event.stopPropagation();
                void onLikeClick(item);
              }
            : undefined
        }
        onFavoriteClick={
          onFavoriteClick
            ? (event) => {
                event.stopPropagation();
                void onFavoriteClick(item);
              }
            : undefined
        }
      />
    ),
    [onFavoriteClick, onItemClick, onLikeClick, showRank],
  );

  const masonry =
    isMasonry && listItems.length > 0 ? (
      <Masonry
        items={listItems}
        render={renderMasonryItem}
        itemKey={(item) => item.id}
        columnWidth={280}
        columnGutter={12}
        rowGutter={12}
        itemHeightEstimate={360}
        overscanBy={KEEP_ALIVE_MASONRY_OVERSCAN}
        className="post-feed-list__masonry"
      />
    ) : undefined;

  return (
    <FeedPanel
      className={[panelClassName, isHotRank ? 'post-feed-list--hot-rank' : '']
        .filter(Boolean)
        .join(' ')}
      loading={loading}
      onRefresh={onRefresh}
      listLayout={isMasonry ? 'masonry' : 'stack'}
      masonry={masonry}
      infinite={
        infinite
          ? { ...infinite, itemCount: infinite.itemCount ?? items.length }
          : undefined
      }
      empty={
        !loading && items.length === 0 ? (
          <EmptyState description={emptyText} />
        ) : undefined
      }
    >
      {isHotRank
        ? listItems.map((item) => (
            <HotRankRowCard
              key={item.id}
              item={item}
              rank={item.rank}
              hotScore={item.hotScore}
              onClick={() => onItemClick(item.id)}
              onLikeClick={
                onLikeClick
                  ? (event) => {
                      event.stopPropagation();
                      void onLikeClick(item);
                    }
                  : undefined
              }
            />
          ))
        : isMasonry
          ? null
          : listItems.map((item) => (
              <ContentCard
                key={item.id}
                authorDecoration={get(item.author.accountId)}
                data={{
                  id: item.id,
                  author: {
                    nickname: item.author.nickname,
                    avatar: item.author.avatar,
                    accountId: item.author.accountId,
                  },
                  title: item.title,
                  content: item.content,
                  postType: item.postType,
                  images: item.images,
                  coverUrl: item.coverUrl,
                  videoUrl: item.videoUrl,
                  refPost: item.refPost,
                  tags: item.tags,
                  gameTags: item.gameTags,
                  viewCount: item.viewCount,
                  likeCount: item.likeCount,
                  commentCount: item.commentCount,
                  liked: item.liked,
                  createdAt: item.createdAt,
                  rank: showRank ? item.rank : undefined,
                  hotScore: showRank ? item.hotScore : undefined,
                }}
                onClick={() => onItemClick(item.id)}
                onLikeClick={
                  onLikeClick
                    ? (event) => {
                        event.stopPropagation();
                        void onLikeClick(item);
                      }
                    : undefined
                }
              />
            ))}
    </FeedPanel>
  );
};

export default memo(PostFeedList);
