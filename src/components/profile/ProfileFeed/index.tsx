import React, { memo, useMemo } from 'react';
import type { FC } from 'react';

import FeedPanel from '@/components/FeedPanel';
import { useUserDecorations } from '@/hooks/useUserDecorations';
import { mapContentPostTypeToNumeric } from '@/utils/postType';
import {
  buildPostActivityPath,
  isProfileActivityItem,
} from '@/utils/profileFeed';

import ProfileFeedEmpty from './parts/ProfileFeedEmpty';
import ProfileFeedItem from './parts/ProfileFeedItem';
import type { IProps } from './types';
import { useProfileFeed } from './useProfileFeed';

const ProfileFeed: FC<IProps> = (props) => {
  const { transitionMinHeight } = props;
  const {
    mainTab,
    isOther = false,
    list,
    loading,
    loadingMore,
    hasMore,
    sentinelRef,
    buildOwnerActionBarItems,
    handleLike,
    handleRefresh,
    handleItemClick,
    handleGoPublish,
    navigateToPost,
  } = useProfileFeed(props);

  const authorIds = useMemo(
    () => list.map((item) => item.author.accountId),
    [list],
  );
  const { get } = useUserDecorations(authorIds);

  const useRowLayout = mainTab === 'posts';
  const feedCards = list.map((item) => {
    const isOwnerPost =
      !isOther && mainTab === 'posts' && item.id && item.articleStatus != null;

    const activityPath = buildPostActivityPath(item);
    const goActivity = () => {
      if (activityPath) {
        navigateToPost(activityPath);
        return;
      }
      handleItemClick(item);
    };
    const goPost = () => {
      if (activityPath) {
        navigateToPost(activityPath);
        return;
      }
      handleItemClick(item);
    };

    const isLikedOrReceivedActivity =
      (mainTab === 'liked' || mainTab === 'received') &&
      isProfileActivityItem(item);

    return (
      <ProfileFeedItem
        key={item.id}
        item={item}
        layout={useRowLayout ? 'row' : 'card'}
        authorDecoration={get(item.author.accountId)}
        ownerPost={
          isOwnerPost
            ? {
                id: String(item.id),
                status: item.articleStatus!,
                postType: mapContentPostTypeToNumeric(item.postType),
                title: item.title,
              }
            : undefined
        }
        buildOwnerActionItems={
          isOwnerPost ? buildOwnerActionBarItems : undefined
        }
        showActions={mainTab !== 'comments' && !isLikedOrReceivedActivity}
        onActivityClick={goActivity}
        onPostClick={goPost}
        onLikeClick={
          mainTab !== 'comments' ? () => handleLike(item) : undefined
        }
      />
    );
  });

  const feedContent = useMemo(() => {
    if (feedCards.length === 0) return null;
    if (!useRowLayout) return feedCards;
    return <div className="profile-feed__rows">{feedCards}</div>;
  }, [feedCards, useRowLayout]);

  return (
    <div
      className="profile-feed-shell"
      style={
        loading && list.length === 0 && transitionMinHeight
          ? { minHeight: transitionMinHeight }
          : undefined
      }
    >
      <FeedPanel
        className={`profile-feed${useRowLayout ? ' profile-feed--rows' : ''}`}
        loading={loading && list.length === 0}
        onRefresh={handleRefresh}
        infinite={{
          sentinelRef,
          loadingMore,
          hasMore,
          itemCount: list.length,
        }}
        empty={
          list.length === 0 ? (
            <ProfileFeedEmpty
              mainTab={mainTab}
              isOther={isOther}
              onGoPublish={handleGoPublish}
            />
          ) : undefined
        }
      >
        {feedContent}
      </FeedPanel>
    </div>
  );
};

export default memo(ProfileFeed);
