import React, { memo } from 'react';
import type { FC } from 'react';
import { Link } from 'react-router-dom';

import type { HeaderBrandConfig } from '../types';

interface HeaderBrandProps {
  brand: HeaderBrandConfig;
}

const HeaderBrand: FC<HeaderBrandProps> = ({ brand }) => (
  <Link to={brand.to} className="app-header__brand">
    <span className="app-header__logo">{brand.logoText}</span>
    <span className="app-header__name">{brand.name}</span>
  </Link>
);

export default memo(HeaderBrand);
