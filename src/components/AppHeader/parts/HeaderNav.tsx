import React, { memo } from 'react';
import type { FC } from 'react';
import { NavLink } from 'react-router-dom';

import type { HeaderNavItem } from '../types';

interface HeaderNavProps {
  items: HeaderNavItem[];
  loggedIn?: boolean;
}

function shouldShowNavItem(item: HeaderNavItem, loggedIn: boolean): boolean {
  if (item.requireAuth && !loggedIn) return false;
  if (item.guestOnly && loggedIn) return false;
  return true;
}

const HeaderNav: FC<HeaderNavProps> = ({ items, loggedIn = false }) => (
  <nav className="app-header__nav">
    {items
      .filter((item) => shouldShowNavItem(item, loggedIn))
      .map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          end={item.end}
          className={({ isActive }) =>
            `app-header__link${isActive ? ' is-active' : ''}`
          }
        >
          {item.label}
        </NavLink>
      ))}
  </nav>
);

export default memo(HeaderNav);
