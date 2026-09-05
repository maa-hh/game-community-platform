import { BRAND_NAME } from '@/constants/brand';

import type {
  HeaderActionsConfig,
  HeaderBrandConfig,
  HeaderNavItem,
  HeaderSearchConfig,
  UserMenuSchemaItem,
} from './types';

export const headerBrand: HeaderBrandConfig = {
  to: '/',
  logoText: 'GC',
  name: BRAND_NAME,
};

export const headerNavItems: HeaderNavItem[] = [
  { to: '/community', label: '社区', end: true },
  { to: '/feed', label: '动态', requireAuth: true },
  { to: '/recommend', label: '推荐' },
  { to: '/shop', label: '商城', requireAuth: true },
  { to: '/games', label: '游戏' },
];

export const headerSearch: HeaderSearchConfig = {
  placeholder: '搜索用户 / 帖子 / 游戏',
  defaultTab: 'all',
};

export const headerActions: HeaderActionsConfig = {
  publishLabel: '发布内容',
  publishTo: '/post/editor',
  loginLabel: '登录',
};

/** 用户下拉菜单结构（行为在 useHeaderActions 注入） */
export const userMenuSchema: UserMenuSchemaItem[] = [
  { key: 'profile', label: '个人主页', icon: 'profile' },
  { key: 'shop', label: '积分商城', icon: 'shop' },
  { type: 'divider' },
  { key: 'logout', label: '退出登录', icon: 'logout' },
];
