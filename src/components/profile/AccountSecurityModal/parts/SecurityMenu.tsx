import React from 'react';
import type { FC } from 'react';
import {
  DeleteOutlined,
  LockOutlined,
  MailOutlined,
  RightOutlined,
} from '@ant-design/icons';

import { securityMenuItems } from '../config';
import type { PanelKey, SecurityMenuIconKey } from '../types';

const ICON_MAP: Record<
  SecurityMenuIconKey,
  React.ComponentType<{ className?: string }>
> = {
  lock: LockOutlined,
  mail: MailOutlined,
  delete: DeleteOutlined,
};

interface IProps {
  onMenuClick: (key: Exclude<PanelKey, 'menu'>) => void;
}

const SecurityMenu: FC<IProps> = ({ onMenuClick }) => {
  return (
    <div className="account-security__menu">
      {securityMenuItems.map((item) => {
        const Icon = ICON_MAP[item.icon];
        return (
          <button
            key={item.key}
            type="button"
            className={`account-security__item${item.danger ? ' is-danger' : ''}`}
            onClick={() => onMenuClick(item.key)}
          >
            <span className="account-security__item-icon">
              <Icon />
            </span>
            <span className="account-security__item-text">
              <strong>{item.title}</strong>
              <span>{item.desc}</span>
            </span>
            <RightOutlined className="account-security__item-arrow" />
          </button>
        );
      })}
    </div>
  );
};

export default SecurityMenu;
