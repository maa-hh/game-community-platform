import React, { memo } from 'react';
import type { FC } from 'react';

import PostRowActionBar from '@/base-ui/PostRowActionBar';
import PostRowPreview from '@/base-ui/PostRowPreview';

import ProfilePostStats from './parts/ProfilePostStats';
import type { ProfilePostCardProps } from './types';
import { mapFeedItemToRowPreview } from './types';

import './style.less';

const ProfilePostCard: FC<ProfilePostCardProps> = ({
  item,
  actionItems,
  footer,
  onPreviewClick,
}) => {
  const previewData = mapFeedItemToRowPreview(item);

  return (
    <article className="profile-post-card">
      <div className="profile-post-card__section profile-post-card__section--preview">
        <PostRowPreview
          data={previewData}
          onClick={onPreviewClick}
          metaFooter={<ProfilePostStats item={item} />}
        />
      </div>

      {footer}

      {actionItems && actionItems.length > 0 ? (
        <div className="profile-post-card__section profile-post-card__section--actions">
          <PostRowActionBar
            items={actionItems}
            className="profile-post-card__action-bar"
          />
        </div>
      ) : null}
    </article>
  );
};

export default memo(ProfilePostCard);

export { mapFeedItemToRowPreview } from './types';
export type { ProfilePostCardProps } from './types';
