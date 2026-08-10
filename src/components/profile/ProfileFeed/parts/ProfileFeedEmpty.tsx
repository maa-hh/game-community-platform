import React from 'react';
import type { FC, ReactNode } from 'react';
import { Button, Empty, Space } from 'antd';

import type { MainTabKey } from '@/types/profile';

interface IProps {
  mainTab: MainTabKey;
  onGoPublish: () => void;
  isOther?: boolean;
}

const ProfileFeedEmpty: FC<IProps> = ({ mainTab, onGoPublish, isOther }) => {
  let description: ReactNode;

  if (isOther) {
    description = '暂无已发布内容';
  } else if (mainTab === 'posts') {
    description = (
      <Space direction="vertical">
        <span>暂无内容</span>
        <Button type="primary" onClick={onGoPublish}>
          去发布
        </Button>
      </Space>
    );
  } else if (mainTab === 'favorites') {
    description = '还没有收藏';
  } else {
    description = '暂无内容';
  }

  return (
    <Empty description={description} image={Empty.PRESENTED_IMAGE_SIMPLE} />
  );
};

export default ProfileFeedEmpty;
