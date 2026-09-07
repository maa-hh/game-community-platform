import React, { memo } from 'react';
import type { FC } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Button, Empty, Spin } from 'antd';
import { SyncOutlined } from '@ant-design/icons';

import UserAvatar from '@/base-ui/UserAvatar';
import SteamCoverImage from '@/base-ui/SteamCoverImage';
import {
  formatSteamAchievementProgress,
  formatSteamPlaytime,
} from '@/utils/formatSteamPlaytime';

import type { ISteamSectionProps } from './types';
import { useSteamSection } from './useSteamSection';
import { buildGameDetailNavigationState } from '@/utils/detailNavigation';
import type { IGameListItem } from '@/types/game';

import './style.less';

const SteamSection: FC<ISteamSectionProps> = ({
  className,
  targetAccountId,
  readOnly = false,
}) => {
  const navigate = useNavigate();
  const location = useLocation();
  const {
    loading,
    binding,
    syncing,
    syncProgress,
    profile,
    library,
    libraryPrivate,
    bound,
    bindSteam,
    syncLibrary,
    unbind,
  } = useSteamSection({ targetAccountId, readOnly });

  const sectionTitle = readOnly ? '游戏账号' : 'Steam 账号';

  return (
    <section className={`steam-section${className ? ` ${className}` : ''}`}>
      <div className="steam-section__head">
        <h2 className="steam-section__title">{sectionTitle}</h2>
        {!readOnly ? (
          <div className="steam-section__actions">
            <Button size="small" onClick={() => navigate('/games')}>
              游戏中心
            </Button>
            {bound ? (
              <>
                <Button
                  size="small"
                  icon={<SyncOutlined spin={syncing} />}
                  loading={syncing}
                  onClick={() => void syncLibrary()}
                >
                  {syncing && syncProgress && syncProgress.total > 0
                    ? `同步 ${syncProgress.processed}/${syncProgress.total}`
                    : '同步'}
                </Button>
                <Button size="small" danger onClick={unbind}>
                  解绑
                </Button>
              </>
            ) : null}
          </div>
        ) : null}
      </div>

      {loading ? (
        <div className="steam-section__loading">
          <Spin />
        </div>
      ) : bound && profile ? (
        <>
          <div className="steam-section__profile">
            <UserAvatar
              name={profile.personaName}
              src={profile.avatarUrl}
              size={56}
            />
            <div className="steam-section__profile-meta">
              <strong className="steam-section__name">
                {profile.personaName}
              </strong>
              {profile.steamLevel != null ? (
                <span className="steam-section__level">
                  Steam 等级 Lv.{profile.steamLevel}
                </span>
              ) : null}
            </div>
          </div>

          {libraryPrivate ? (
            <Empty
              className="steam-section__empty"
              description="该用户游戏库未公开"
            />
          ) : library.length > 0 ? (
            <div className="steam-section__library">
              {library.map((game) => {
                const achievementText = formatSteamAchievementProgress(
                  game.achievementUnlocked,
                  game.achievementTotal,
                );
                return (
                  <button
                    key={game.appId}
                    type="button"
                    className="steam-section__game"
                    onClick={() =>
                      navigate(`/game/${game.appId}`, {
                        state: buildGameDetailNavigationState(location, {
                          appId: game.appId,
                          name: game.name,
                          coverUrl: game.coverUrl,
                        } satisfies IGameListItem),
                      })
                    }
                    title={game.name}
                  >
                    <SteamCoverImage
                      appId={game.appId}
                      name={game.name}
                      coverUrl={game.coverUrl}
                      iconUrl={game.iconUrl}
                      className="steam-section__game-cover"
                      placeholderClassName="steam-section__game-cover steam-section__game-cover--placeholder"
                    />
                    <div className="steam-section__game-body">
                      <span className="steam-section__game-name">
                        {game.name}
                      </span>
                      <div className="steam-section__game-stats">
                        <span>
                          总时长 {formatSteamPlaytime(game.playtimeForever)}
                        </span>
                        <span>
                          近两周 {formatSteamPlaytime(game.playtimeTwoWeeks)}
                        </span>
                        <button
                          type="button"
                          className="steam-section__achievement-link"
                          onClick={(event) => {
                            event.stopPropagation();
                            navigate(`/game/${game.appId}?tab=stats`, {
                              state: buildGameDetailNavigationState(location, {
                                appId: game.appId,
                                name: game.name,
                                coverUrl: game.coverUrl,
                              } satisfies IGameListItem),
                            });
                          }}
                        >
                          成就 {achievementText ?? '—'}
                        </button>
                      </div>
                    </div>
                  </button>
                );
              })}
            </div>
          ) : (
            <Empty
              className="steam-section__empty"
              description={
                readOnly ? '暂无已同步的游戏' : '游戏库为空，点击同步拉取'
              }
            />
          )}
        </>
      ) : (
        <div className="steam-section__unbound">
          {readOnly ? (
            <Empty
              className="steam-section__empty"
              description="该用户尚未绑定 Steam"
            />
          ) : (
            <>
              <p className="steam-section__hint">
                绑定 Steam 后可同步游戏库，并在个人页展示。
              </p>
              <Button
                type="primary"
                loading={binding}
                onClick={() => void bindSteam()}
              >
                绑定 Steam
              </Button>
            </>
          )}
        </div>
      )}
    </section>
  );
};

export default memo(SteamSection);
