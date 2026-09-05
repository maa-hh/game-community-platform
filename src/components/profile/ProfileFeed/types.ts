import type { MainTabKey, PostSubTabKey } from '@/types/profile';

export interface ProfileFeedAuthor {
  accountId?: number;
  username?: string;
  avatar?: string;
}

export interface IProps {
  mainTab: MainTabKey;
  postSubTab: PostSubTabKey;
  /** 切换 Tab 时由父层在旧内容还在页面上时测得的高度，避免加载态造成页面跳动。 */
  transitionMinHeight?: number;
  /** 查看他人主页时传入作者 accountId */
  targetAccountId?: number;
  /** 他人主页作者信息（用于卡片展示） */
  authorUser?: ProfileFeedAuthor;
  isOther?: boolean;
}
