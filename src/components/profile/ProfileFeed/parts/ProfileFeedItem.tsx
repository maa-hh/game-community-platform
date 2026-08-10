import React, { useMemo, useState } from 'react';
import type { FC } from 'react';

import ContentCard from '@/base-ui/ContentCard';
import ProfilePostCard from '@/components/profile/ProfilePostCard';
import ProfileActivityCard from '@/components/profile/ProfileFeed/parts/ProfileActivityCard';
import ShareSheet from '@/components/ShareSheet';
import type { PostRowActionItem } from '@/base-ui/PostRowActionBar';
import type { IUserDecoration } from '@/types/cosmetic';
import type { FeedItemData } from '@/types/profile';
import { ARTICLE_STATUS } from '@/service/content';
import { isProfileActivityItem } from '@/utils/profileFeed';

interface OwnerPostParams {
  id: string;
  status: number;
  postType?: number;
  title?: string;
}

interface IProps {
  item: FeedItemData;
  layout?: 'row' | 'card';
  authorDecoration?: IUserDecoration;
  ownerPost?: OwnerPostParams;
  buildOwnerActionItems?: (
    params: OwnerPostParams & { onShare?: () => void },
  ) => PostRowActionItem[];
  showActions?: boolean;
  activityShowRefPost?: boolean;
  onActivityClick: () => void;
  onPostClick: () => void;
  onLikeClick?: () => void;
}

const ProfileFeedItem: FC<IProps> = ({
  item,
  layout = 'card',
  authorDecoration,
  ownerPost,
  buildOwnerActionItems,
  showActions = true,
  activityShowRefPost,
  onActivityClick,
  onPostClick,
  onLikeClick,
}) => {
  const [shareOpen, setShareOpen] = useState(false);
  const canShare =
    item.articleStatus === ARTICLE_STATUS.PUBLISHED && Boolean(item.id);

  const actionItems = useMemo(() => {
    if (!ownerPost || !buildOwnerActionItems) return undefined;
    return buildOwnerActionItems({
      ...ownerPost,
      onShare: canShare ? () => setShareOpen(true) : undefined,
    });
  }, [buildOwnerActionItems, canShare, ownerPost]);

  if (isProfileActivityItem(item)) {
    return (
      <div className="profile-feed__item">
        <ProfileActivityCard
          item={item}
          showRefPost={activityShowRefPost ?? item.activityShowRefPost ?? true}
          showActions={showActions}
          onActivityClick={onActivityClick}
          onPostClick={(e) => {
            e.stopPropagation();
            onPostClick();
          }}
          onLikeClick={
            onLikeClick
              ? (e) => {
                  e.stopPropagation();
                  onLikeClick();
                }
              : undefined
          }
        />
      </div>
    );
  }

  if (layout === 'row') {
    return (
      <div className="profile-feed__item">
        <ProfilePostCard
          item={item}
          actionItems={actionItems}
          onPreviewClick={onPostClick}
        />

        {canShare && item.id ? (
          <ShareSheet
            open={shareOpen}
            articleId={item.id}
            articleTitle={item.title}
            articleSummary={item.summary || item.content}
            coverUrl={item.coverUrl || item.images?.[0]}
            videoUrl={item.videoUrl}
            postType={item.postType}
            author={{
              accountId: item.author.accountId,
              nickname: item.author.nickname,
              avatar: item.author.avatar,
            }}
            viewCount={item.viewCount}
            commentCount={item.commentCount}
            likeCount={item.likeCount}
            onClose={() => setShareOpen(false)}
          />
        ) : null}
      </div>
    );
  }

  return (
    <div className="profile-feed__item">
      <ContentCard
        authorDecoration={authorDecoration}
        data={{
          id: item.id,
          author: item.author,
          title: item.title,
          summary: item.summary,
          content: item.content,
          postType: item.postType,
          coverUrl: item.coverUrl,
          videoUrl: item.videoUrl,
          refPost: item.refPost,
          images: item.images?.length
            ? item.images
            : item.cover
              ? [item.cover]
              : undefined,
          tags: item.tags,
          viewCount: showActions ? item.viewCount : undefined,
          commentCount: showActions ? item.commentCount : undefined,
          likeCount: showActions ? item.likeCount : undefined,
          liked: item.liked,
          createdAt: item.createdAt,
        }}
        onClick={onPostClick}
        onLikeClick={
          onLikeClick
            ? (e) => {
                e.stopPropagation();
                onLikeClick();
              }
            : undefined
        }
      />
    </div>
  );
};

export default ProfileFeedItem;
