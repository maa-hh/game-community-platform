export const PROFILE_DATA_DOMAIN = {
  BASE: 'profile.base',
  STATS: 'profile.stats',
  FOLLOWING: 'profile.following',
  FEED: 'profile.feed',
  FOLLOWERS: 'profile.followers',
  POSTS: 'profile.posts',
  HISTORY: 'profile.history',
  LIKED: 'profile.liked',
  RECEIVED: 'profile.received',
  FAVORITES: 'profile.favorites',
  COMMENTS: 'profile.comments',
} as const;

export type ProfileDataDomain =
  (typeof PROFILE_DATA_DOMAIN)[keyof typeof PROFILE_DATA_DOMAIN];

/** Selector fallback must keep a stable reference to avoid render loops. */
export const EMPTY_PROFILE_DATA_DOMAINS: readonly ProfileDataDomain[] = [];

export function isProfileDataDomain(value: string): value is ProfileDataDomain {
  return Object.values(PROFILE_DATA_DOMAIN).includes(
    value as ProfileDataDomain,
  );
}
