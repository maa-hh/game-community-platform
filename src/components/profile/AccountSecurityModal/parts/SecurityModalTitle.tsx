import React from 'react';
import type { FC } from 'react';
import { Button } from 'antd';
import { ArrowLeftOutlined } from '@ant-design/icons';

interface IProps {
  title: string;
  showBack: boolean;
  submitting: boolean;
  onBack: () => void;
}

const SecurityModalTitle: FC<IProps> = ({
  title,
  showBack,
  submitting,
  onBack,
}) => {
  return (
    <div className="account-security__title">
      {showBack && (
        <Button
          type="text"
          size="small"
          icon={<ArrowLeftOutlined />}
          onClick={onBack}
          disabled={submitting}
        />
      )}
      <span>{title}</span>
    </div>
  );
};

export default SecurityModalTitle;
