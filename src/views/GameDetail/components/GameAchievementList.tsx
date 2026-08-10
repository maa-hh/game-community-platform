import React, { memo, useMemo, useState } from 'react';
import type { FC } from 'react';
import { Button, Typography } from 'antd';
import { DownOutlined, TrophyOutlined, UpOutlined } from '@ant-design/icons';

import type { IGameAchievement } from '@/types/game';
import SteamAchievementImage from '@/base-ui/SteamAchievementImage';

import './GameAchievementList.less';

const { Title } = Typography;

const COLUMNS = 3;
const COLLAPSED_ROWS = 3;
const COLLAPSED_COUNT = COLUMNS * COLLAPSED_ROWS;

export interface GameAchievementListProps {
  achievements: IGameAchievement[];
  total?: number;
}

const GameAchievementList: FC<GameAchievementListProps> = ({
  achievements,
  total,
}) => {
  const [expanded, setExpanded] = useState(false);
  const visible = useMemo(
    () => (expanded ? achievements : achievements.slice(0, COLLAPSED_COUNT)),
    [achievements, expanded],
  );
  const hasMore = achievements.length > COLLAPSED_COUNT;

  if (achievements.length === 0) {
    return null;
  }

  return (
    <div className="game-achievement-list">
      <Title level={5} className="game-achievement-list__title">
        <TrophyOutlined /> Steam 成就
        {total ? `（共 ${total} 个）` : ''}
      </Title>
      <div
        className={`game-achievement-list__grid${
          !expanded && hasMore ? ' is-collapsed' : ''
        }`}
      >
        {visible.map((item) => (
          <div key={item.name} className="game-achievement-list__item">
            <SteamAchievementImage
              src={item.iconUrl}
              className="game-achievement-list__icon"
              fallback={
                <span
                  className="game-achievement-list__icon game-achievement-list__icon--placeholder"
                  aria-hidden
                >
                  <TrophyOutlined />
                </span>
              }
            />
            <span>{item.name}</span>
          </div>
        ))}
      </div>
      {hasMore ? (
        <Button
          type="link"
          className="game-achievement-list__toggle"
          icon={expanded ? <UpOutlined /> : <DownOutlined />}
          onClick={() => setExpanded((prev) => !prev)}
        >
          {expanded ? '收起' : `展开全部（${achievements.length} 个成就）`}
        </Button>
      ) : null}
    </div>
  );
};

export default memo(GameAchievementList);
