export interface HeaderNavItem {
  to: string;
  label: string;
  /** NavLink end：路径完全匹配时才高亮（首页必须 true） */
  end?: boolean;
  /** 仅登录后显示 */
  requireAuth?: boolean;
  /** 仅游客显示 */
  guestOnly?: boolean;
}

export interface HeaderBrandConfig {
  to: string;
  logoText: string;
  name: string;
}

export interface HeaderSearchConfig {
  placeholder: string;
  defaultTab: 'all' | 'posts' | 'users';
}

export interface HeaderActionsConfig {
  publishLabel: string;
  publishTo: string;
  loginLabel: string;
}

export type UserMenuIconKey = 'profile' | 'shop' | 'admin' | 'logout';

export interface UserMenuActionItem {
  key: string;
  label: string;
  icon: UserMenuIconKey;
}

export interface UserMenuDividerItem {
  type: 'divider';
}

export type UserMenuSchemaItem = UserMenuActionItem | UserMenuDividerItem;

export function isUserMenuDivider(
  item: UserMenuSchemaItem,
): item is UserMenuDividerItem {
  return 'type' in item && item.type === 'divider';
}
