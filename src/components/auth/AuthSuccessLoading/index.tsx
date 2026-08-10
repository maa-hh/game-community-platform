import React, { memo } from 'react';
import type { FC } from 'react';
import { Spin, Typography } from 'antd';

import type { IProps } from './types';

const { Text } = Typography;

const AuthSuccessLoading: FC<IProps> = ({ tip, children }) => {
  return (
    <div className="login-success">
      <Spin size="large" />
      <Text type="secondary" className="login-success__tip">
        {tip}
      </Text>
      {children ? <div className="login-success__extra">{children}</div> : null}
    </div>
  );
};

export default memo(AuthSuccessLoading);
