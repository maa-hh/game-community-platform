import React, { memo, useMemo } from 'react';
import type { FC } from 'react';

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

  const renderMasonryCard = (item: (typeof items)[number]) => (
    <FeedMasonryCard
      key={item.id}
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
  );

  return (
    <FeedPanel
      className={[panelClassName, isHotRank ? 'post-feed-list--hot-rank' : '']
        .filter(Boolean)
        .join(' ')}
      loading={loading}
      onRefresh={onRefresh}
      listLayout={isMasonry ? 'masonry' : 'stack'}
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
          ? listItems.map((item) => renderMasonryCard(item))
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
