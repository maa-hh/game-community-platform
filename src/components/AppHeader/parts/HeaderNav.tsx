import React, { memo } from 'react';
import { Badge } from 'antd';
import type { FC } from 'react';
import { NavLink } from 'react-router-dom';

import { primaryNavigationState } from '@/utils/primaryNavigation';

import type { HeaderNavItem } from '../types';

interface HeaderNavProps {
  items: HeaderNavItem[];
  loggedIn?: boolean;
  feedUnreadCount?: number;
}

function shouldShowNavItem(item: HeaderNavItem, loggedIn: boolean): boolean {
  if (item.requireAuth && !loggedIn) return false;
  if (item.guestOnly && loggedIn) return false;
  return true;
}

const HeaderNav: FC<HeaderNavProps> = ({
  items,
  loggedIn = false,
  feedUnreadCount = 0,
}) => (
  <nav className="app-header__nav">
    {items
      .filter((item) => shouldShowNavItem(item, loggedIn))
      .map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          end={item.end}
          state={primaryNavigationState}
          className={({ isActive }) =>
            `app-header__link${isActive ? ' is-active' : ''}`
          }
        >
          {item.to === '/feed' ? (
            <Badge
              className="app-header__feed-badge"
              count={feedUnreadCount}
              overflowCount={99}
              size="small"
            >
              {item.label}
            </Badge>
          ) : (
            item.label
          )}
        </NavLink>
      ))}
  </nav>
);

export default memo(HeaderNav);
