import React, { memo, useMemo } from 'react';
import type { FC, MouseEvent } from 'react';
import { CheckOutlined, PlusOutlined } from '@ant-design/icons';

import type { IGameListItem } from '@/types/game';
import {
  formatGameStudio,
  formatSiteScore,
  formatSteamReviewCount,
  formatSteamScore,
} from '@/utils/formatGameScore';

import OverflowTagRow from '@/base-ui/OverflowTagRow';
import type { OverflowTagRowItem } from '@/base-ui/OverflowTagRow';
import GameCardPriceOverlay from '@/base-ui/GameCardPriceOverlay';
import StatAction from '@/base-ui/StatAction';
import SteamCoverImage from '@/base-ui/SteamCoverImage';
import { preloadGameDetail } from '@/router/preload';

import GameCoverPoster from './GameCoverPoster';

import './GameCard.less';

export interface IGameCardProps {
  game: IGameListItem;
  followed?: boolean;
  showFollow?: boolean;
  followLoading?: boolean;
  onClick?: () => void;
  onFollow?: (event: MouseEvent<HTMLElement>) => void;
}

const GameCard: FC<IGameCardProps> = ({
  game,
  followed = false,
  showFollow = false,
  followLoading = false,
  onClick,
  onFollow,
}) => {
  const preloadDetail = () => {
    void preloadGameDetail();
  };
  const displayName = game.name?.trim() || `游戏 ${game.appId}`;
  const genreItems = useMemo<OverflowTagRowItem[]>(
    () =>
      (game.genres?.filter(Boolean) ?? []).map((tag) => ({
        key: tag,
        text: tag,
      })),
    [game.genres],
  );
  const studio = formatGameStudio(game.developer, game.publisher);
  const releaseDate = game.releaseDate?.trim() || '暂无发行日期';
  const steamScoreText = formatSteamScore(game.steamScore);
  const steamReviewText = formatSteamReviewCount(game.steamReviewCount);
  const siteScoreText = formatSiteScore(game.avgScore, game.reviewCount);

  return (
    <article
      className="game-card"
      onClick={onClick}
      onMouseEnter={preloadDetail}
      onPointerDown={preloadDetail}
      role="presentation"
    >
      <div className="game-card__media">
        <SteamCoverImage
          appId={game.appId}
          name={displayName}
          coverUrl={game.coverUrl}
          className="game-card__cover"
          fallback={
            <GameCoverPoster
              title={displayName}
              seed={game.appId}
              className="game-card__poster"
            />
          }
        />
        {showFollow ? (
          <button
            type="button"
            className={`game-card__follow-chip${followed ? ' is-followed' : ''}`}
            disabled={followLoading}
            aria-label={followed ? '已关注' : '加入我的游戏'}
            onClick={(event) => {
              event.stopPropagation();
              onFollow?.(event);
            }}
          >
            {followed ? <CheckOutlined /> : <PlusOutlined />}
          </button>
        ) : null}
        {game.rank != null ? (
          <span
            className="game-card__rank"
            aria-label={`榜单第 ${game.rank} 名`}
          >
            #{game.rank}
          </span>
        ) : null}
        <GameCardPriceOverlay price={game.price} />
      </div>

      <div className="game-card__body">
        <h3 className="game-card__title" title={displayName}>
          {displayName}
        </h3>

        {genreItems.length > 0 ? (
          <OverflowTagRow
            className="game-card__tags"
            preset="game"
            items={genreItems}
          />
        ) : (
          <div className="game-card__tags">
            <span className="game-card__tag game-card__tag--empty">
              <span className="game-card__tag-text">暂无类型</span>
            </span>
          </div>
        )}

        <p className="game-card__line" title={studio}>
          {studio}
        </p>
        <p className="game-card__line" title={releaseDate}>
          {releaseDate}
        </p>
        <p
          className="game-card__line"
          title={`Steam 评分 ${steamScoreText}${steamReviewText ? ` · ${steamReviewText}` : ''}`}
        >
          Steam 评分 <span>{steamScoreText}</span>
          {steamReviewText ? (
            <>
              {' '}
              · <span>{steamReviewText}</span>
            </>
          ) : null}
        </p>
        <p
          className="game-card__line game-card__line--site"
          title={`本站评分 ${siteScoreText}`}
        >
          本站评分 <span>{siteScoreText}</span>
        </p>

        <footer className="game-card__footer">
          <StatAction
            kind="comment"
            count={game.discussCount ?? 0}
            size="sm"
            className="game-card__discuss"
          />
        </footer>
      </div>
    </article>
  );
};

export default memo(GameCard);
