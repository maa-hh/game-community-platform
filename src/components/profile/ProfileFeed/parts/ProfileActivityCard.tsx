import React from 'react';
import type { FC, MouseEventHandler } from 'react';

import ShareCard from '@/components/ShareCard';
import ProfileUserLink from '@/components/ProfileUserLink';
import StatAction from '@/base-ui/StatAction';
import type { FeedItemData } from '@/types/profile';
import { formatCardTime } from '@/utils/formatTime';
import { sanitizeGameShareContent } from '@/utils/gameRepost';

import './ProfileActivityCard.style.less';

interface IProps {
  item: FeedItemData;
  showRefPost?: boolean;
  showActions?: boolean;
  onActivityClick: () => void;
  onPostClick?: MouseEventHandler<HTMLElement>;
  onLikeClick?: MouseEventHandler<HTMLButtonElement>;
}

const ProfileActivityCard: FC<IProps> = ({
  item,
  showRefPost = true,
  showActions = false,
  onActivityClick,
  onPostClick,
  onLikeClick,
}) => {
  const {
    activityQuote,
    activityActor,
    activitySubject,
    parentQuote,
    refPost,
    createdAt,
  } = item;
  if (!refPost) return null;
  const hasQuote = activityQuote != null && activityQuote !== '';
  if (!hasQuote && !activityActor && !activitySubject) return null;

  const shouldShowRefPost = showRefPost && item.activityShowRefPost !== false;
  const displayText = (value?: string) =>
    sanitizeGameShareContent({ content: value }) || '';

  const renderQuoteBody = () => {
    if (activitySubject) {
      return (
        <p className="profile-activity-card__quote">
          <ProfileUserLink
            accountId={activitySubject.accountId}
            nickname={activitySubject.nickname}
            avatar={activitySubject.avatar}
            showAvatar={false}
            size={0}
          />
          <span className="profile-activity-card__quote-sep">：</span>
          <span className="profile-activity-card__quote-text">
            {displayText(activitySubject.content)}
          </span>
        </p>
      );
    }

    return hasQuote ? (
      <p className="profile-activity-card__quote">
        {displayText(activityQuote)}
      </p>
    ) : null;
  };

  return (
    <article className="profile-activity-card">
      {activityActor ? (
        <div className="profile-activity-card__actor">
          <ProfileUserLink
            accountId={activityActor.accountId}
            nickname={activityActor.nickname}
            avatar={activityActor.avatar}
            size={32}
          />
          {createdAt ? (
            <span className="profile-activity-card__time">
              {formatCardTime(createdAt)}
            </span>
          ) : null}
        </div>
      ) : null}

      <div
        className={`profile-activity-card__quote-only is-clickable${
          !shouldShowRefPost ? ' is-highlight' : ''
        }`}
        role="button"
        tabIndex={0}
        onClick={() => onActivityClick()}
        onKeyDown={(e) => {
          if (e.key === 'Enter') onActivityClick();
        }}
      >
        {!activityActor && createdAt ? (
          <span className="profile-activity-card__time profile-activity-card__time--inline">
            {formatCardTime(createdAt)}
          </span>
        ) : null}
        {renderQuoteBody()}
      </div>

      {parentQuote ? (
        <div
          className="profile-activity-card__parent is-clickable"
          role="button"
          tabIndex={0}
          onClick={() => onActivityClick()}
          onKeyDown={(e) => {
            if (e.key === 'Enter') onActivityClick();
          }}
        >
          <ProfileUserLink
            accountId={parentQuote.accountId}
            nickname={parentQuote.nickname}
            avatar={parentQuote.avatar}
            showAvatar={false}
            size={0}
            className="profile-activity-card__parent-link"
          />
          <span className="profile-activity-card__parent-sep">：</span>
          <span className="profile-activity-card__parent-text">
            {displayText(parentQuote.content)}
          </span>
        </div>
      ) : null}

      {shouldShowRefPost ? (
        <div className="profile-activity-card__post">
          <ShareCard data={refPost} onClick={onPostClick} />
        </div>
      ) : null}

      {showActions &&
      (item.viewCount != null ||
        item.commentCount != null ||
        item.likeCount != null ||
        onLikeClick) ? (
        <footer className="profile-activity-card__actions">
          {item.viewCount != null ? (
            <StatAction kind="view" count={item.viewCount} size="sm" />
          ) : null}
          {item.commentCount != null ? (
            <StatAction kind="comment" count={item.commentCount} size="sm" />
          ) : null}
          {(item.likeCount != null || onLikeClick) && (
            <StatAction
              kind="like"
              count={item.likeCount ?? 0}
              active={item.liked}
              size="sm"
              stopPropagation
              onClick={onLikeClick}
            />
          )}
        </footer>
      ) : null}
    </article>
  );
};

export default ProfileActivityCard;
