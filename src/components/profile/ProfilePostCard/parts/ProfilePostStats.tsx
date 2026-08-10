import React, { memo } from 'react';
import type { FC } from 'react';

import StatAction from '@/base-ui/StatAction';
import type { FeedItemData } from '@/types/profile';

interface ProfilePostStatsProps {
  item: FeedItemData;
}

const ProfilePostStats: FC<ProfilePostStatsProps> = ({ item }) => {
  return (
    <div className="profile-post-stats">
      <StatAction
        kind="view"
        count={item.viewCount ?? 0}
        size="sm"
        className="profile-post-stats__item"
      />
      <StatAction
        kind="like"
        count={item.likeCount ?? 0}
        active={item.liked}
        size="sm"
        className="profile-post-stats__item"
      />
      <StatAction
        kind="comment"
        count={item.commentCount ?? 0}
        size="sm"
        className="profile-post-stats__item"
      />
      <StatAction
        kind="share"
        count={item.shareCount ?? 0}
        size="sm"
        className="profile-post-stats__item"
      />
    </div>
  );
};

export default memo(ProfilePostStats);
