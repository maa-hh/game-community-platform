import React, { memo, useCallback, useMemo, useRef } from 'react';
import type { FC } from 'react';
import {
  MasonryScroller,
  useContainerPosition,
  usePositioner,
  useResizeObserver,
} from 'masonic';
import type { MasonryProps, RenderComponentProps } from 'masonic';
import { useWindowSize } from '@react-hook/window-size';

import ContentCard from '@/base-ui/ContentCard';
import FeedMasonryCard from '@/base-ui/FeedMasonryCard';
import HotRankRowCard from '@/base-ui/HotRankRowCard';
import FeedPanel from '@/components/FeedPanel';
import EmptyState from '@/components/EmptyState';
import { useUserDecorations } from '@/hooks/useUserDecorations';
import { useActiveRouteView } from '@/hooks/useActiveRouteView';
import {
  applyPostInteraction,
  getPostInteractionScope,
} from '@/hooks/usePostInteraction';
import { useAppSelector } from '@/store';

import { sortHotRankItems } from '@/utils/sortHotRankItems';
import type { LatestPostItem } from '@/types/post';

import type { IPostFeedListProps } from './types';

import './style.less';

// Masonic 的 overscanBy 单位是视口高度倍数。保持适度预渲染，避免滚动和
// 互动更新时同时参与布局的卡片过多；页面实例由 MainLayout keep-alive 保留。
const MASONRY_OVERSCAN = 3;

interface ResettableMasonryProps<Item> extends MasonryProps<Item> {
  layoutKey: number;
}

const ResettableMasonry = <Item,>({
  layoutKey,
  ssrWidth,
  ssrHeight,
  ...props
}: ResettableMasonryProps<Item>) => {
  const containerRef = useRef<HTMLElement | null>(null);
  const isActiveRouteView = useActiveRouteView();
  const windowSize = useWindowSize({
    initialWidth: ssrWidth,
    initialHeight: ssrHeight,
  });
  const containerPosition = useContainerPosition(containerRef, [
    ...windowSize,
    isActiveRouteView,
  ]);
  const positioner = usePositioner(
    {
      // 容器尚未完成测量时不能回退到 viewport 宽度，否则绝对定位的卡片
      // 会按窗口宽度计算，突破 MainLayout 的内容盒。
      width: containerPosition.width,
      columnWidth: props.columnWidth,
      columnGutter: props.columnGutter,
      rowGutter: props.rowGutter,
      columnCount: props.columnCount,
      maxColumnCount: props.maxColumnCount,
      maxColumnWidth: props.maxColumnWidth,
    },
    [layoutKey],
  );
  const resizeObserver = useResizeObserver(positioner);

  return (
    <MasonryScroller
      {...props}
      offset={containerPosition.offset}
      height={windowSize[1]}
      containerRef={containerRef}
      positioner={positioner}
      resizeObserver={resizeObserver}
    />
  );
};

function isRenderablePost(
  item: LatestPostItem | null | undefined,
): item is LatestPostItem {
  if (!item || typeof item !== 'object') return false;
  if (typeof item.id !== 'string' || item.id.trim() === '') return false;
  return Boolean(item.author && typeof item.author === 'object');
}

const PostFeedList: FC<IPostFeedListProps> = ({
  items,
  loading = false,
  refreshing = false,
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
  const renderableItems = useMemo(() => {
    const seenIds = new Set<string>();
    return items.filter((item) => {
      if (!isRenderablePost(item) || seenIds.has(item.id)) return false;
      seenIds.add(item.id);
      return true;
    });
  }, [items]);
  const accountId = useAppSelector((state) => state.auth.user?.accountId);
  const interactionScope = getPostInteractionScope(accountId);
  const interactionMap = useAppSelector(
    (state) => state.postInteraction.byAccount[interactionScope],
  );
  const syncedItems = useMemo(
    () =>
      renderableItems.map((item) =>
        applyPostInteraction(item, interactionMap?.[item.id]),
      ),
    [interactionMap, renderableItems],
  );
  const authorIds = useMemo(
    () => syncedItems.map((item) => item.author.accountId),
    [syncedItems],
  );
  const isMasonry = layout === 'masonry';
  const isHotRank = layout === 'hotRank';
  const decorationAuthorIds = isMasonry || isHotRank ? [] : authorIds;
  const { get } = useUserDecorations(decorationAuthorIds);

  const listItems = useMemo(() => {
    if (isHotRank || (isMasonry && showRank)) {
      return sortHotRankItems(syncedItems);
    }
    // masonic 的 positioner 按数组索引缓存位置。分页追加必须保持已有
    // 项目的顺序，否则旧卡片会沿用原索引的位置，新卡片可能覆盖在旧卡片上。
    // 社区流、关注流和搜索结果都由接口负责返回顺序，这里不要再对全量列表排序。
    return syncedItems;
  }, [isHotRank, isMasonry, showRank, syncedItems]);

  // Masonic 的 positioner 按数组索引缓存位置：连续分页追加时应复用缓存，
  // 但刷新、删项或接口返回顺序变化时必须重建，否则旧索引可能指向不存在的项。
  const masonryLayoutRef = useRef<{ ids: string[]; epoch: number }>({
    ids: [],
    epoch: 0,
  });
  const masonryResetKey = useMemo(() => {
    const nextIds = listItems.map((item) => item.id);
    const previousIds = masonryLayoutRef.current.ids;
    const isAppendOnly =
      previousIds.length > 0 &&
      nextIds.length >= previousIds.length &&
      previousIds.every((id, index) => nextIds[index] === id);

    if (previousIds.length > 0 && !isAppendOnly) {
      masonryLayoutRef.current.epoch += 1;
    }
    masonryLayoutRef.current.ids = nextIds;
    return masonryLayoutRef.current.epoch;
  }, [listItems]);

  const renderMasonryItem = useCallback(
    ({ data: item }: RenderComponentProps<(typeof items)[number]>) => (
      <FeedMasonryCard
        item={item}
        rank={showRank ? item.rank : undefined}
        hotScore={showRank ? item.hotScore : undefined}
        onClick={() => onItemClick(item)}
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
      <ResettableMasonry
        layoutKey={masonryResetKey}
        items={listItems}
        render={renderMasonryItem}
        itemKey={(item) => item.id}
        columnWidth={280}
        columnGutter={12}
        rowGutter={12}
        itemHeightEstimate={360}
        overscanBy={MASONRY_OVERSCAN}
        className="post-feed-list__masonry"
      />
    ) : undefined;

  return (
    <FeedPanel
      className={[panelClassName, isHotRank ? 'post-feed-list--hot-rank' : '']
        .filter(Boolean)
        .join(' ')}
      loading={loading}
      refreshing={refreshing}
      onRefresh={onRefresh}
      listLayout={isMasonry ? 'masonry' : 'stack'}
      masonry={masonry}
      infinite={
        infinite
          ? {
              ...infinite,
              itemCount: infinite.itemCount ?? syncedItems.length,
            }
          : undefined
      }
      empty={
        !loading && syncedItems.length === 0 ? (
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
              onClick={() => onItemClick(item)}
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
                  likePending: item.likePending,
                  createdAt: item.createdAt,
                  rank: showRank ? item.rank : undefined,
                  hotScore: showRank ? item.hotScore : undefined,
                }}
                onClick={() => onItemClick(item)}
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
