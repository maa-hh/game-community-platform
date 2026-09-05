import React from 'react';
import type { FC, ReactNode } from 'react';
import { Button, Empty } from 'antd';

import type { MainTabKey } from '@/types/profile';

interface IProps {
  mainTab: MainTabKey;
  onGoPublish: () => void;
  isOther?: boolean;
}

const ProfileFeedEmpty: FC<IProps> = ({ mainTab, onGoPublish, isOther }) => {
  let title = '这里还没有内容';
  let hint = '内容会在这里显示';
  let action: ReactNode;

  if (isOther) {
    title = 'TA 还没有发布内容';
  } else if (mainTab === 'posts') {
    hint = '分享一段游戏见闻，和大家聊聊吧';
    action = (
      <Button type="primary" onClick={onGoPublish}>
        去发布
      </Button>
    );
  } else if (mainTab === 'history') {
    title = '还没有浏览记录';
    hint = '浏览过的帖子会保存在这里';
  } else if (mainTab === 'favorites') {
    title = '还没有收藏内容';
    hint = '收藏喜欢的帖子后，可以随时回来查看';
  }

  return (
    <Empty
      className="profile-feed-empty"
      description={
        <div className="profile-feed-empty__content">
          <strong>{title}</strong>
          <span>{hint}</span>
          {action}
        </div>
      }
      image={Empty.PRESENTED_IMAGE_SIMPLE}
    />
  );
};

export default ProfileFeedEmpty;
