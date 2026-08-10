import type { MainTabKey, PostSubTabKey } from '@/types/profile';

export interface ProfileFeedAuthor {
  accountId?: number;
  username?: string;
  avatar?: string;
}

export interface IProps {
  mainTab: MainTabKey;
  postSubTab: PostSubTabKey;
  /** 查看他人主页时传入作者 accountId */
  targetAccountId?: number;
  /** 他人主页作者信息（用于卡片展示） */
  authorUser?: ProfileFeedAuthor;
  isOther?: boolean;
}
