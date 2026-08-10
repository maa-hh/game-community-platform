import type {
  MainTabKey,
  OtherProfileTabKey,
  PostSubTabKey,
} from '@/types/profile';

export type {
  FeedItemData,
  MainTabKey,
  OtherProfileTabKey,
  PostSubTabKey,
  ProfileStatKey,
  ProfileStats,
} from '@/types/profile';
export { formatCount } from '@/utils/formatCount';

export const MAIN_TABS: { key: MainTabKey; label: string }[] = [
  { key: 'posts', label: '帖子' },
  { key: 'history', label: '浏览历史' },
  { key: 'liked', label: '赞过' },
  { key: 'received', label: '获赞' },
  { key: 'favorites', label: '收藏' },
  { key: 'comments', label: '评论' },
];

export const POST_SUB_TABS: { key: PostSubTabKey; label: string }[] = [
  { key: 'published', label: '已发布' },
  { key: 'draft', label: '草稿' },
];

export const OTHER_PROFILE_TABS: { key: OtherProfileTabKey; label: string }[] =
  [
    { key: 'posts', label: '帖子' },
    { key: 'steam', label: '游戏账号' },
  ];
export {
  MOCK_FAN_USERS,
  MOCK_FEEDS,
  MOCK_FOLLOW_USERS,
  MOCK_STATS,
} from '@/mock/profile';
