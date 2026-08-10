import React, { memo, useMemo } from 'react';
import type { FC } from 'react';
import { Empty, Spin, Typography } from 'antd';

import CoverGallery from '@/base-ui/CoverGallery';
import SteamRichHtml from '@/base-ui/SteamRichHtml';
import type { IGameDetail } from '@/types/game';

import GameAchievementList from './GameAchievementList';
import GameStoreBar from './GameStoreBar';

import './GameIntroPanel.less';

const { Paragraph, Title } = Typography;

export interface GameIntroPanelProps {
  detail: IGameDetail;
  refreshing?: boolean;
}

const GameIntroPanel: FC<GameIntroPanelProps> = ({
  detail,
  refreshing = false,
}) => {
  const trailer = detail.movies?.[0];
  const trailerSrc = trailer?.mp4Url || trailer?.webmUrl;
  const screenshotItems = useMemo(
    () =>
      (detail.screenshots || [])
        .map((item) => ({
          src: item.thumbnailUrl || item.fullUrl,
          previewSrc: item.fullUrl || item.thumbnailUrl,
        }))
        .filter((item) => Boolean(item.src)) as {
        src: string;
        previewSrc: string;
      }[],
    [detail.screenshots],
  );
  const hasBody =
    Boolean(detail.shortDescription) ||
    Boolean(detail.aboutHtml) ||
    screenshotItems.length > 0 ||
    Boolean(trailerSrc) ||
    (detail.categories?.length ?? 0) > 0 ||
    (detail.achievementHighlights?.length ?? 0) > 0 ||
    Boolean(detail.pcRequirementsMin || detail.pcRequirementsRec);

  if (!hasBody && !detail.steamUrl && !refreshing) {
    return <Empty description="暂无游戏介绍" />;
  }

  return (
    <section className="game-intro-panel">
      {refreshing ? (
        <div className="game-intro-panel__refreshing" role="status">
          <Spin size="small" />
          <span>基础信息已加载，正在后台补全游戏介绍与媒体…</span>
        </div>
      ) : null}
      {trailerSrc ? (
        <div className="game-intro-panel__section">
          <Title level={5} className="game-intro-panel__section-title">
            预告片
          </Title>
          <div className="game-intro-panel__trailer">
            <video
              className="game-intro-panel__trailer-video"
              controls
              preload="metadata"
              poster={trailer?.thumbnailUrl}
              playsInline
            >
              {trailer?.mp4Url ? (
                <source src={trailer.mp4Url} type="video/mp4" />
              ) : null}
              {trailer?.webmUrl ? (
                <source src={trailer.webmUrl} type="video/webm" />
              ) : null}
            </video>
            {trailer?.name ? (
              <span className="game-intro-panel__trailer-name">
                {trailer.name}
              </span>
            ) : null}
          </div>
        </div>
      ) : null}

      {screenshotItems.length > 0 ? (
        <div className="game-intro-panel__section">
          <Title level={5} className="game-intro-panel__section-title">
            游戏截图
          </Title>
          <CoverGallery
            images={screenshotItems}
            className="game-intro-panel__gallery"
          />
        </div>
      ) : null}

      {detail.shortDescription ? (
        <Paragraph className="game-intro-panel__lead">
          {detail.shortDescription}
        </Paragraph>
      ) : null}

      {detail.aboutHtml ? (
        <div className="game-intro-panel__section">
          <Title level={5} className="game-intro-panel__section-title">
            详细介绍
          </Title>
          <SteamRichHtml
            html={detail.aboutHtml}
            className="game-intro-panel__about-html"
          />
        </div>
      ) : null}

      {detail.categories && detail.categories.length > 0 ? (
        <div className="game-intro-panel__section">
          <Title level={5} className="game-intro-panel__section-title">
            游戏特性
          </Title>
          <div className="game-intro-panel__chip-row">
            {detail.categories.map((item) => (
              <span key={item} className="game-intro-panel__chip">
                {item}
              </span>
            ))}
          </div>
        </div>
      ) : null}

      {detail.metacritic?.score != null ? (
        <div className="game-intro-panel__section">
          <Title level={5} className="game-intro-panel__section-title">
            媒体评分
          </Title>
          <div className="game-intro-panel__metacritic">
            <span className="game-intro-panel__metacritic-score">
              {detail.metacritic.score}
            </span>
            <span>Metacritic</span>
            {detail.metacritic.url ? (
              <a
                href={detail.metacritic.url}
                target="_blank"
                rel="noopener noreferrer"
              >
                查看详情
              </a>
            ) : null}
          </div>
        </div>
      ) : null}

      {detail.achievementHighlights &&
      detail.achievementHighlights.length > 0 ? (
        <div className="game-intro-panel__section">
          <GameAchievementList
            achievements={detail.achievementHighlights}
            total={detail.achievementTotal}
          />
        </div>
      ) : null}

      {detail.pcRequirementsMin || detail.pcRequirementsRec ? (
        <div className="game-intro-panel__section">
          <Title level={5} className="game-intro-panel__section-title">
            系统需求
          </Title>
          {detail.pcRequirementsMin ? (
            <SteamRichHtml
              html={detail.pcRequirementsMin}
              className="game-intro-panel__requirements"
            />
          ) : null}
          {detail.pcRequirementsRec ? (
            <SteamRichHtml
              html={detail.pcRequirementsRec}
              className="game-intro-panel__requirements game-intro-panel__requirements--rec"
            />
          ) : null}
        </div>
      ) : null}

      <GameStoreBar detail={detail} />
    </section>
  );
};

export default memo(GameIntroPanel);
