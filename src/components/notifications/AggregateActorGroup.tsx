import React from 'react';
import type { FC } from 'react';

import ProfileUserLink from '@/components/ProfileUserLink';
import type { INotificationActor } from '@/types/notification';

import './style.less';

const MAX_VISIBLE = 5;

interface AggregateActorGroupProps {
  actors: INotificationActor[];
  total?: number;
}

const AggregateActorGroup: FC<AggregateActorGroupProps> = ({
  actors,
  total,
}) => {
  const count = total ?? actors.length;
  const visible = actors.slice(0, MAX_VISIBLE);
  const rest = Math.max(0, count - visible.length);

  return (
    <div className="aggregate-actor-group">
      <div className="aggregate-actor-group__avatars">
        {visible.map((actor) => (
          <ProfileUserLink
            key={actor.accountId}
            accountId={actor.accountId}
            nickname={actor.username || '玩家'}
            avatar={actor.avatar}
            size={28}
            showNickname={false}
            className="aggregate-actor-group__avatar"
          />
        ))}
        {rest > 0 ? (
          <span className="aggregate-actor-group__more">…</span>
        ) : null}
      </div>
      {count > 1 ? (
        <span className="aggregate-actor-group__text">等 {count} 人</span>
      ) : visible[0] ? (
        <ProfileUserLink
          accountId={visible[0].accountId}
          nickname={visible[0].username || '玩家'}
          avatar={visible[0].avatar}
          size={0}
          showAvatar={false}
        />
      ) : null}
    </div>
  );
};

export default AggregateActorGroup;
