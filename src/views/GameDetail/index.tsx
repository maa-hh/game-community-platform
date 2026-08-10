import React, { useEffect, useState } from 'react';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import {
  Button,
  Empty,
  Input,
  List,
  Pagination,
  Rate,
  Select,
  Space,
  Spin,
  Tabs,
  Typography,
  message,
} from 'antd';
import { CheckOutlined, PlusOutlined } from '@ant-design/icons';

import PageLoading from '@/base-ui/PageLoading';
import SteamCoverImage from '@/base-ui/SteamCoverImage';
import PostFeedList from '@/components/PostFeedList';
import { useRequireLogin } from '@/hooks/useRequireLogin';
import { formatApiError } from '@/utils/apiError';
import { formatSteamScore } from '@/utils/formatGameScore';
import { formatGamePrice } from '@/utils/formatGamePrice';
import {
  buildReturnNavigationState,
  canGoBackInApp,
} from '@/utils/returnNavigation';

import GameDetailTopBar from './components/GameDetailTopBar';
import GameIntroPanel from './components/GameIntroPanel';
import GameReviewItem from './components/GameReviewItem';
import GameShareSheet from './components/GameShareSheet';
import GameStatsPanel from './components/GameStatsPanel';
import { useGameDetail, type GameDetailTabKey } from './useGameDetail';

import './style.less';

const { Text, Title } = Typography;
const { TextArea } = Input;

const TAB_ITEMS: { key: GameDetailTabKey; label: string }[] = [
  { key: 'intro', label: '介绍' },
  { key: 'stats', label: '统计' },
  { key: 'reviews', label: '评分' },
  { key: 'discuss', label: '讨论区' },
];

function GameDetail() {
  const location = useLocation();
  const navigate = useNavigate();
  const { appId: appIdParam = '' } = useParams();
  const appId = Number(appIdParam);
  const { requireLogin, isLoggedIn } = useRequireLogin();

  const {
    activeTab,
    setActiveTab,
    detail,
    ratingStats,
    reviews,
    reviewsLoading,
    reviewsPage,
    reviewsTotal,
    reviewSort,
    setReviewSort,
    reviewPageSize,
    loadReviews,
    myReview,
    myReviewLoading,
    detailLoading,
    detailRefreshing,
    detailError,
    reviewSubmitting,
    saveMyReview,
    removeMyReview,
    followed,
    followLoading,
    toggleFollow,
    steamStats,
    steamStatsLoading,
    steamAchievementSyncing,
    syncSteamAchievements,
    discussions,
  } = useGameDetail(appId);

  const [score, setScore] = useState(8);
  const [reviewContent, setReviewContent] = useState('');
  const [shareOpen, setShareOpen] = useState(false);

  const locationState =
    typeof location.state === 'object' && location.state !== null
      ? (location.state as {
          returnTo?: unknown;
          returnScrollY?: unknown;
        })
      : null;
  const returnTo =
    typeof locationState?.returnTo === 'string' ? locationState.returnTo : null;
  const returnScrollY =
    typeof locationState?.returnScrollY === 'number' &&
    Number.isFinite(locationState.returnScrollY) &&
    locationState.returnScrollY >= 0
      ? locationState.returnScrollY
      : null;

  useEffect(() => {
    if (myReview) {
      setScore(myReview.score);
      setReviewContent(myReview.content || '');
    } else {
      setScore(8);
      setReviewContent('');
    }
  }, [myReview]);

  const handleBack = () => {
    if (document.activeElement instanceof HTMLElement) {
      document.activeElement.blur();
    }
    if (canGoBackInApp()) {
      navigate(-1);
      return;
    }
    if (returnTo) {
      navigate(returnTo, {
        replace: true,
        preventScrollReset: true,
        flushSync: true,
        state:
          returnScrollY == null ? undefined : { restoreScrollY: returnScrollY },
      });
      return;
    }
    navigate('/games');
  };

  const handleSaveReview = async () => {
    if (!requireLogin()) return;
    if (score < 1) {
      message.warning('请选择 1–10 分');
      return;
    }
    await saveMyReview(score, reviewContent);
  };

  const handleDiscussRefresh = async () => {
    try {
      await discussions.reload();
    } catch {
      message.error(formatApiError('加载讨论失败', new Error()));
    }
  };

  const handlePostDiscuss = () => {
    if (!requireLogin()) return;
    const params = new URLSearchParams({
      gameAppId: String(appId),
      gameName: detail?.name || '',
    });
    navigate(`/post/editor?${params.toString()}`);
  };

  if (detailLoading) {
    return (
      <div className="game-detail game-detail--loading">
        <PageLoading />
      </div>
    );
  }

  const priceText = detail ? formatGamePrice(detail.price) : null;

  if (detailError || !detail) {
    return (
      <div className="game-detail game-detail--error">
        <div className="game-detail__top-dock">
          <div className="game-detail__align-track">
            <GameDetailTopBar onBack={handleBack} />
          </div>
        </div>
        <Empty description={detailError || '游戏不存在'} />
      </div>
    );
  }

  const steamScoreText = formatSteamScore(detail.steamReviewScore);

  return (
    <div className="game-detail">
      <div className="game-detail__top-dock">
        <div className="game-detail__align-track">
          <GameDetailTopBar
            name={detail.name}
            price={detail.price}
            steamUrl={detail.steamUrl}
            onBack={handleBack}
            onShare={() => setShareOpen(true)}
          />
        </div>
      </div>

      <header className="game-detail__hero">
        <div className="game-detail__hero-body">
          <div className="game-detail__cover-wrap">
            <SteamCoverImage
              appId={detail.appId}
              name={detail.name}
              coverUrl={detail.coverUrl}
              className="game-detail__cover"
              placeholderClassName="game-detail__cover game-detail__cover--placeholder"
            />
          </div>
          <div className="game-detail__hero-info">
            <Title level={2} className="game-detail__name">
              {detail.name}
            </Title>
            {detail.tags && detail.tags.length > 0 ? (
              <div className="game-detail__meta-tags">
                {detail.tags.map((tag) => (
                  <span key={tag} className="game-detail__meta-tag">
                    {tag}
                  </span>
                ))}
              </div>
            ) : null}
            {(detail.developer || detail.publisher || detail.releaseDate) && (
              <Text type="secondary" className="game-detail__meta-line">
                {[detail.developer, detail.publisher, detail.releaseDate]
                  .filter(Boolean)
                  .join(' · ')}
              </Text>
            )}
            <div className="game-detail__stats">
              {detail.steamReviewScore != null ? (
                <span className="game-detail__stat">
                  Steam {steamScoreText}
                </span>
              ) : null}
              {detail.metacritic?.score != null ? (
                <span className="game-detail__stat game-detail__stat--meta">
                  Metacritic {detail.metacritic.score}
                </span>
              ) : null}
              {ratingStats && ratingStats.reviewCount > 0 ? (
                <span className="game-detail__stat game-detail__stat--site">
                  本站 {ratingStats.averageScore.toFixed(1)} 分
                </span>
              ) : null}
            </div>
            <Button
              className="game-detail__follow-btn"
              type={followed ? 'default' : 'primary'}
              icon={followed ? <CheckOutlined /> : <PlusOutlined />}
              loading={followLoading}
              onClick={() => {
                if (!requireLogin()) return;
                void toggleFollow();
              }}
            >
              {followed ? '已在我的游戏' : '加入我的游戏'}
            </Button>
          </div>
        </div>
      </header>

      <Tabs
        activeKey={activeTab}
        onChange={(key) => setActiveTab(key as GameDetailTabKey)}
        items={TAB_ITEMS}
        className="game-detail__tabs"
        size="large"
      />

      {activeTab !== 'discuss' ? (
        <div className="game-detail__panel">
          {activeTab === 'intro' && (
            <GameIntroPanel detail={detail} refreshing={detailRefreshing} />
          )}

          {activeTab === 'stats' && (
            <GameStatsPanel
              loading={steamStatsLoading}
              loggedIn={isLoggedIn}
              stats={steamStats}
              syncing={steamAchievementSyncing}
              onSync={() => void syncSteamAchievements()}
              onLogin={() => {
                requireLogin();
              }}
            />
          )}

          {activeTab === 'reviews' && (
            <section className="game-detail__reviews">
              <div className="game-detail__rating-summary">
                <div className="game-detail__rating-score">
                  <span className="game-detail__rating-value">
                    {ratingStats?.reviewCount
                      ? ratingStats.averageScore.toFixed(1)
                      : '—'}
                  </span>
                  <Text type="secondary">
                    {ratingStats?.reviewCount
                      ? `${ratingStats.reviewCount} 条评价`
                      : '暂无评分'}
                  </Text>
                </div>
              </div>

              <div className="game-detail__my-review">
                <Title level={5}>我的评价</Title>
                {myReviewLoading ? (
                  <Spin />
                ) : (
                  <Space
                    direction="vertical"
                    size="middle"
                    style={{ width: '100%' }}
                  >
                    <div className="game-detail__rate-row">
                      <Text>评分</Text>
                      <Rate
                        count={10}
                        value={score}
                        onChange={(value) => setScore(value)}
                        disabled={reviewSubmitting}
                      />
                      <Text type="secondary">{score} / 10</Text>
                    </div>
                    <TextArea
                      rows={4}
                      value={reviewContent}
                      onChange={(event) => setReviewContent(event.target.value)}
                      placeholder="写下你的游玩体验（可选）"
                      maxLength={2000}
                      showCount
                      disabled={reviewSubmitting}
                    />
                    <Space>
                      <Button
                        type="primary"
                        loading={reviewSubmitting}
                        onClick={() => void handleSaveReview()}
                      >
                        {myReview ? '更新评价' : '提交评价'}
                      </Button>
                      {myReview ? (
                        <Button
                          danger
                          loading={reviewSubmitting}
                          onClick={() => void removeMyReview()}
                        >
                          删除评价
                        </Button>
                      ) : null}
                    </Space>
                  </Space>
                )}
              </div>

              <div className="game-detail__review-list">
                <div className="game-detail__review-list-head">
                  <Title level={5}>全部评价</Title>
                  <Select
                    size="small"
                    value={reviewSort}
                    options={[
                      { label: '最近', value: 'latest' },
                      { label: '最高点赞', value: 'hot' },
                    ]}
                    onChange={setReviewSort}
                  />
                </div>
                <List
                  loading={reviewsLoading}
                  dataSource={reviews}
                  locale={{ emptyText: '暂无评价' }}
                  renderItem={(item) => (
                    <GameReviewItem
                      review={item}
                      onRequireLogin={requireLogin}
                    />
                  )}
                />
                {reviewsTotal > reviewPageSize ? (
                  <Pagination
                    className="game-detail__review-pager"
                    current={reviewsPage}
                    pageSize={reviewPageSize}
                    total={reviewsTotal}
                    onChange={(page) => void loadReviews(page)}
                    showSizeChanger={false}
                  />
                ) : null}
              </div>
            </section>
          )}
        </div>
      ) : (
        <section className="game-detail__discuss">
          <div className="game-detail__discuss-head">
            <Title level={5} className="game-detail__discuss-title">
              社区讨论
            </Title>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={handlePostDiscuss}
            >
              发帖讨论
            </Button>
          </div>
          <PostFeedList
            panelClassName="feed-panel--embedded"
            items={discussions.items}
            loading={discussions.loading}
            emptyText="暂无讨论，来发第一条吧"
            onRefresh={handleDiscussRefresh}
            onItemClick={(id) =>
              navigate(`/post/${id}`, {
                state: buildReturnNavigationState(location),
              })
            }
            onLikeClick={discussions.handleLike}
            infinite={{
              sentinelRef: discussions.sentinelRef,
              loadingMore: discussions.loadingMore,
              hasMore: discussions.hasMore,
              itemCount: discussions.items.length,
            }}
          />
        </section>
      )}

      <GameShareSheet
        open={shareOpen}
        detail={detail}
        priceText={priceText}
        onClose={() => setShareOpen(false)}
      />
    </div>
  );
}

export default GameDetail;
