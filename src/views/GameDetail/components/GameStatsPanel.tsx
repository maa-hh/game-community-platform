import React, { memo } from 'react';
import type { FC } from 'react';
import {
  Button,
  Empty,
  Progress,
  Skeleton,
  Spin,
  Table,
  Typography,
} from 'antd';
import {
  ReloadOutlined,
  TrophyFilled,
  TrophyOutlined,
} from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';

import type { ISteamGameStats, ISteamUserAchievement } from '@/types/game';
import SteamAchievementImage from '@/base-ui/SteamAchievementImage';
import { formatDateTime } from '@/utils/formatTime';
import {
  formatSteamAchievementProgress,
  formatSteamPlaytime,
} from '@/utils/formatSteamPlaytime';

import './GameStatsPanel.less';

const { Text, Title } = Typography;

export interface GameStatsPanelProps {
  loading: boolean;
  loggedIn: boolean;
  stats: ISteamGameStats | null;
  syncing?: boolean;
  onSync?: () => void;
  onLogin?: () => void;
}

function formatGlobalPercent(value?: number | null): string {
  if (value == null || !Number.isFinite(value)) {
    return '—';
  }
  return `${value.toFixed(1)}%`;
}

const GameStatsPanel: FC<GameStatsPanelProps> = ({
  loading,
  loggedIn,
  stats,
  syncing = false,
  onSync,
  onLogin,
}) => {
  if (!loggedIn) {
    return (
      <Empty
        className="game-stats-panel__empty"
        description="登录后查看你的 Steam 游玩统计"
      >
        {onLogin ? (
          <button
            type="button"
            className="game-stats-panel__link"
            onClick={onLogin}
          >
            去登录
          </button>
        ) : null}
      </Empty>
    );
  }

  if (loading && !stats) {
    return (
      <div className="game-stats-panel__loading">
        <Skeleton active paragraph={{ rows: 5 }} />
      </div>
    );
  }

  if (!stats?.owned) {
    return (
      <Empty
        className="game-stats-panel__empty"
        description="你的 Steam 库中暂无此游戏，或尚未绑定 Steam"
      />
    );
  }

  const achievementText = formatSteamAchievementProgress(
    stats.achievementUnlocked,
    stats.achievementTotal,
  );
  const achievementPercent =
    stats.achievementTotal && stats.achievementTotal > 0
      ? Math.round(
          ((stats.achievementUnlocked ?? 0) / stats.achievementTotal) * 100,
        )
      : 0;

  const achievementLoading =
    (stats.achievementTotal ?? 0) > 0 &&
    (stats.achievements?.length ?? 0) === 0 &&
    stats.achievementStatus !== 'FAILED' &&
    stats.achievementStatus !== 'NOT_AVAILABLE';

  if (achievementLoading) {
    return (
      <section className="game-stats-panel">
        <div className="game-stats-panel__summary">
          <div className="game-stats-panel__card">
            <Text type="secondary">游戏时长</Text>
            <strong>{formatSteamPlaytime(stats.playtimeForever)}</strong>
          </div>
          <div className="game-stats-panel__card">
            <Text type="secondary">近两周时长</Text>
            <strong>{formatSteamPlaytime(stats.playtimeTwoWeeks)}</strong>
          </div>
          <div className="game-stats-panel__card">
            <Text type="secondary">最后运行</Text>
            <strong>
              {stats.lastPlayedAt ? formatDateTime(stats.lastPlayedAt) : '—'}
            </strong>
          </div>
          <div className="game-stats-panel__card">
            <Text type="secondary">成就进度</Text>
            <strong>{achievementText}</strong>
          </div>
        </div>
        <div className="game-stats-panel__achievement-loading">
          <Text type="secondary">
            成就数据正在同步，保留当前统计并加载成就定义…
          </Text>
          {Array.from({ length: 5 }).map((_, index) => (
            <div
              className="game-stats-panel__achievement-loading-row"
              key={index}
            >
              <span className="game-stats-panel__achievement-loading-icon" />
              <span className="game-stats-panel__achievement-loading-line" />
            </div>
          ))}
        </div>
        <Button icon={<ReloadOutlined />} loading={syncing} onClick={onSync}>
          手动同步成就
        </Button>
      </section>
    );
  }

  const columns: ColumnsType<ISteamUserAchievement> = [
    {
      title: '成就',
      key: 'name',
      render: (_, record) => (
        <div
          className={`game-stats-panel__achievement${
            record.unlocked ? ' is-unlocked' : ''
          }`}
        >
          <SteamAchievementImage
            src={record.iconUrl}
            className="game-stats-panel__achievement-icon"
            fallback={
              <span
                className="game-stats-panel__achievement-icon game-stats-panel__achievement-icon--placeholder"
                aria-hidden
              >
                <TrophyOutlined />
              </span>
            }
          />
          <span
            className={`game-stats-panel__trophy game-stats-panel__trophy--${
              record.globalPercent != null && record.globalPercent <= 10
                ? 'gold'
                : 'silver'
            }`}
            title={
              record.globalPercent == null
                ? '全球获取率未知'
                : `全球获取率 ${formatGlobalPercent(record.globalPercent)}`
            }
          >
            <TrophyFilled />
          </span>
          <span className="game-stats-panel__achievement-name">
            {record.name}
          </span>
        </div>
      ),
    },
    {
      title: '获得条件',
      dataIndex: 'description',
      key: 'description',
      render: (value?: string) => value?.trim() || '—',
    },
    {
      title: '获得时间',
      dataIndex: 'unlockTime',
      key: 'unlockTime',
      width: 168,
      render: (value?: string, record?: ISteamUserAchievement) =>
        record?.unlocked && value ? formatDateTime(value) : '—',
    },
    {
      title: '全球获得率',
      dataIndex: 'globalPercent',
      key: 'globalPercent',
      width: 112,
      align: 'right',
      render: (value?: number) => formatGlobalPercent(value),
    },
  ];

  return (
    <section className="game-stats-panel">
      {loading ? (
        <div className="game-stats-panel__refreshing" role="status">
          <Spin size="small" />
          <span>正在更新统计</span>
        </div>
      ) : null}
      <div className="game-stats-panel__summary">
        <div className="game-stats-panel__card">
          <Text type="secondary">游戏时长</Text>
          <strong>{formatSteamPlaytime(stats.playtimeForever)}</strong>
        </div>
        <div className="game-stats-panel__card">
          <Text type="secondary">近两周时长</Text>
          <strong>{formatSteamPlaytime(stats.playtimeTwoWeeks)}</strong>
        </div>
        <div className="game-stats-panel__card">
          <Text type="secondary">最后运行</Text>
          <strong>
            {stats.lastPlayedAt ? formatDateTime(stats.lastPlayedAt) : '—'}
          </strong>
        </div>
        <div className="game-stats-panel__card">
          <Text type="secondary">成就进度</Text>
          <strong>{achievementText ?? '—'}</strong>
        </div>
      </div>

      <div className="game-stats-panel__toolbar">
        <Typography.Text type="secondary">
          {stats.achievementStatus === 'LOADING' ||
          stats.achievementStatus === 'SYNCING'
            ? '成就正在后台同步，当前显示上一次成功快照'
            : stats.achievementSyncedAt
              ? `成就数据更新时间：${formatDateTime(stats.achievementSyncedAt)}`
              : '成就数据尚未同步'}
        </Typography.Text>
        <Button
          size="small"
          icon={<ReloadOutlined />}
          loading={syncing}
          onClick={onSync}
        >
          手动同步成就
        </Button>
      </div>

      <div className="game-stats-panel__trophy-legend">
        <span>
          <TrophyFilled className="game-stats-panel__trophy game-stats-panel__trophy--gold" />{' '}
          全球获取率 ≤ 10%
        </span>
        <span>
          <TrophyFilled className="game-stats-panel__trophy game-stats-panel__trophy--silver" />{' '}
          其余获取率
        </span>
      </div>

      {stats.achievementTotal && stats.achievementTotal > 0 ? (
        <div className="game-stats-panel__progress">
          <div className="game-stats-panel__progress-head">
            <Title level={5} className="game-stats-panel__progress-title">
              <TrophyOutlined /> 成就进度
            </Title>
            <Text type="secondary">{achievementText}</Text>
          </div>
          <Progress
            percent={achievementPercent}
            showInfo={false}
            strokeColor="var(--color-primary)"
          />
        </div>
      ) : null}

      <div className="game-stats-panel__list">
        <Title level={5} className="game-stats-panel__list-title">
          全部成就
        </Title>
        <Table<ISteamUserAchievement>
          rowKey={(record) => record.apiName || record.name}
          columns={columns}
          dataSource={stats.achievements ?? []}
          pagination={false}
          locale={{ emptyText: '暂无成就数据' }}
          size="middle"
        />
      </div>
    </section>
  );
};

export default memo(GameStatsPanel);
