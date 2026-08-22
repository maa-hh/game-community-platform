import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import {
  Button,
  Empty,
  Modal,
  Spin,
  Tag,
  Tabs,
  Typography,
  message,
} from 'antd';
import {
  CameraOutlined,
  EditOutlined,
  FlagOutlined,
  SafetyCertificateOutlined,
  StopOutlined,
} from '@ant-design/icons';

import PageSubTopBar from '@/base-ui/PageSubTopBar';
import UserAvatarWithFrame from '@/base-ui/UserAvatarWithFrame';
import ProfileBgBackdrop from '@/base-ui/ProfileBgBackdrop';
import FollowButton from '@/components/FollowButton';
import ProfileUserLink from '@/components/ProfileUserLink';
import ReportModal from '@/components/ReportModal';
import { useReportModal } from '@/hooks/useReportModal';
import { useRequireLogin } from '@/hooks/useRequireLogin';
import { formatApiError } from '@/utils/apiError';

import { useAppDispatch, useAppSelector } from '@/store';
import { fetchCurrentUserAction } from '@/store/modules/auth';
import { FIELD_AUDIT, isFieldBusy } from '@/service/types';
import ProfileFeed from '@/components/profile/ProfileFeed';
import ProfileUserListModal, {
  type ProfileUserListType,
} from '@/components/profile/ProfileUserListModal';
import {
  checkBlockByAccountApi,
  checkFollowByAccountApi,
  fetchProfileSocialStatsByAccountApi,
  toggleBlockByAccountApi,
  toggleFollowByAccountApi,
} from '@/service/social';
import EditUsernameModal from '@/components/profile/EditUsernameModal';
import EditSignatureModal from '@/components/profile/EditSignatureModal';
import AvatarViewerModal from '@/components/profile/AvatarViewerModal';
import AccountSecurityModal from '@/components/profile/AccountSecurityModal';
import SteamSection from '@/components/profile/SteamSection';
import { useProfileView } from '@/hooks/useProfileView';
import { useDecorationRegistry } from '@/hooks/useDecorationRegistry';
import { resolveAvatarFrameAsset } from '@/constants/avatarFrameCatalog';
import { profileBgTextTheme } from '@/utils/cosmeticAsset';
import { resolveProfileBgAsset } from '@/constants/profileBgCatalog';
import {
  MAIN_TABS,
  MOCK_STATS,
  OTHER_PROFILE_TABS,
  POST_SUB_TABS,
  formatCount,
  type MainTabKey,
  type OtherProfileTabKey,
  type PostSubTabKey,
  type ProfileStatKey,
  type ProfileStats,
} from '@/views/Profile/constants';

import './style.less';

const { Text } = Typography;

function parseProfileMainTab(value: string | null): MainTabKey {
  return MAIN_TABS.some((tab) => tab.key === value)
    ? (value as MainTabKey)
    : 'posts';
}

function parseProfileSubTab(value: string | null): PostSubTabKey {
  return POST_SUB_TABS.some((tab) => tab.key === value)
    ? (value as PostSubTabKey)
    : 'published';
}

function parseOtherProfileTab(value: string | null): OtherProfileTabKey {
  return OTHER_PROFILE_TABS.some((tab) => tab.key === value)
    ? (value as OtherProfileTabKey)
    : 'posts';
}

function auditTag(status?: number | null) {
  if (status === FIELD_AUDIT.AUDITING) {
    return (
      <Tag color="processing" className="profile-hero__audit-tag">
        审核中
      </Tag>
    );
  }
  if (status === FIELD_AUDIT.HUMAN_REVIEW) {
    return (
      <Tag color="warning" className="profile-hero__audit-tag">
        人工审核
      </Tag>
    );
  }
  return null;
}

function Profile() {
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const dispatch = useAppDispatch();
  const { user } = useAppSelector((state) => state.auth);
  const [mainTab, setMainTab] = useState<MainTabKey>(() =>
    parseProfileMainTab(searchParams.get('tab')),
  );
  const [otherTab, setOtherTab] = useState<OtherProfileTabKey>(() =>
    parseOtherProfileTab(searchParams.get('tab')),
  );
  const [postSubTab, setPostSubTab] = useState<PostSubTabKey>(() =>
    parseProfileSubTab(searchParams.get('subtab')),
  );
  const [usernameOpen, setUsernameOpen] = useState(false);
  const [signatureOpen, setSignatureOpen] = useState(false);
  const [avatarOpen, setAvatarOpen] = useState(false);
  const [securityOpen, setSecurityOpen] = useState(false);
  const [stats, setStats] = useState<ProfileStats>(MOCK_STATS);
  const [userListOpen, setUserListOpen] = useState(false);
  const [userListType, setUserListType] =
    useState<ProfileUserListType>('following');
  const feedAnchorRef = useRef<HTMLDivElement | null>(null);
  const { isSelf, isOther, viewUser, loading: viewLoading } = useProfileView();
  const { requireLogin } = useRequireLogin();
  const { reportOpen, reportTarget, openReport, closeReport } =
    useReportModal();
  const [followed, setFollowed] = useState(false);
  const [blocked, setBlocked] = useState(false);

  useEffect(() => {
    void dispatch(fetchCurrentUserAction());
  }, [dispatch]);

  useEffect(() => {
    const targetAccountId = isOther ? viewUser?.accountId : user?.accountId;
    if (!targetAccountId) return;
    void fetchProfileSocialStatsByAccountApi(Number(targetAccountId)).then(
      setStats,
    );
  }, [isOther, user?.accountId, viewUser?.accountId]);

  useEffect(() => {
    const tab = searchParams.get('tab');
    const subtab = searchParams.get('subtab');
    if (isOther) {
      setOtherTab(parseOtherProfileTab(tab));
    } else {
      const nextMainTab = parseProfileMainTab(tab);
      setMainTab(nextMainTab);
      setPostSubTab(
        nextMainTab === 'posts' ? parseProfileSubTab(subtab) : 'published',
      );
    }
  }, [isOther, searchParams]);

  useEffect(() => {
    const raw = (location.state as { postSubTab?: string } | null)?.postSubTab;
    if (!raw) return;
    const tab: PostSubTabKey = raw === 'published' ? 'published' : 'draft';
    setMainTab('posts');
    setPostSubTab(tab);
    const nextParams = new URLSearchParams(searchParams);
    nextParams.set('tab', 'posts');
    nextParams.set('subtab', tab);
    navigate(
      { pathname: location.pathname, search: `?${nextParams.toString()}` },
      { replace: true, state: null },
    );
  }, [location.pathname, location.state, navigate, searchParams]);

  useEffect(() => {
    if (!isSelf || searchParams.get('steam') !== 'success') return;
    message.success('Steam 绑定成功');
    const next = new URLSearchParams(searchParams);
    next.delete('steam');
    setSearchParams(next, { replace: true });
    window.dispatchEvent(new CustomEvent('steam-bind-success'));
  }, [isSelf, searchParams, setSearchParams]);

  const username =
    (isFieldBusy(user?.usernameAuditStatus)
      ? user?.pendingUsername
      : user?.username) ||
    user?.username ||
    user?.email?.split('@')[0] ||
    '玩家';

  const displayAvatar = isFieldBusy(user?.avatarAuditStatus)
    ? user?.pendingAvatarUrl || user?.avatar
    : user?.avatar;

  const signatureRaw = isFieldBusy(user?.signatureAuditStatus)
    ? user?.pendingSignature
    : user?.signature;
  const signature = signatureRaw?.trim();
  const accountId = user?.accountId;
  const bioText = signature || '这个人很懒，还没有签名';

  const heroName = isOther ? viewUser?.username || '用户' : username;
  const heroAvatar = isOther ? viewUser?.avatar : displayAvatar;
  const heroAccountId = isOther ? viewUser?.accountId : accountId;
  const heroBio = isOther
    ? viewUser?.signature?.trim() || '这个人很懒，还没有签名'
    : bioText;
  const heroDecoration = useDecorationRegistry(heroAccountId);
  const heroBgAssetJson = heroDecoration?.profileBg?.assetJson;
  const heroBgAsset = resolveProfileBgAsset(
    heroDecoration?.profileBg?.code,
    heroBgAssetJson,
  );
  const heroBgAssetResolvedJson = heroBgAsset
    ? JSON.stringify(heroBgAsset)
    : heroBgAssetJson;
  const hasHeroBg = Boolean(heroBgAsset?.bgImage);
  const heroTextTheme = profileBgTextTheme(heroBgAssetResolvedJson);
  const heroFrameAsset = resolveAvatarFrameAsset(
    heroDecoration?.avatarFrame?.code,
    heroDecoration?.avatarFrame?.assetJson,
  );
  const hasHeroFrame = Boolean(heroFrameAsset?.frameUrl);

  const usernameBusy = isFieldBusy(user?.usernameAuditStatus);
  const signatureBusy = isFieldBusy(user?.signatureAuditStatus);
  const avatarBusy = isFieldBusy(user?.avatarAuditStatus);

  const mainTabItems = useMemo(
    () =>
      MAIN_TABS.map((tab) => ({
        key: tab.key,
        label: tab.label,
      })),
    [],
  );

  const postSubItems = useMemo(
    () =>
      POST_SUB_TABS.map((tab) => ({
        key: tab.key,
        label: tab.label,
      })),
    [],
  );

  const otherTabItems = useMemo(
    () =>
      OTHER_PROFILE_TABS.map((tab) => ({
        key: tab.key,
        label: tab.label,
      })),
    [],
  );

  const statItems: { key: ProfileStatKey; label: string; value: number }[] = [
    { key: 'following', label: '关注', value: stats.following },
    { key: 'followers', label: '粉丝', value: stats.followers },
    { key: 'likes', label: '获赞', value: stats.likes },
    { key: 'favorites', label: '收藏', value: stats.favorites },
  ];

  const scrollToFeed = useCallback(() => {
    const anchor = feedAnchorRef.current;
    if (!anchor) return;

    window.requestAnimationFrame(() => {
      window.requestAnimationFrame(() => {
        const dock = document.querySelector<HTMLElement>(
          '.profile-page__top-dock',
        );
        const dockBottom = dock?.getBoundingClientRect().bottom ?? 0;
        const targetTop =
          anchor.getBoundingClientRect().top + window.scrollY - dockBottom - 12;
        window.scrollTo({
          top: Math.max(0, targetTop),
          behavior: 'smooth',
        });
      });
    });
  }, []);

  const handleStatClick = (key: ProfileStatKey) => {
    if (key === 'following' || key === 'followers') {
      setUserListType(key);
      setUserListOpen(true);
      return;
    }
    if (key === 'likes') {
      setMainTab('received');
      const nextParams = new URLSearchParams(searchParams);
      nextParams.set('tab', 'received');
      nextParams.delete('subtab');
      setSearchParams(nextParams, {
        replace: true,
        preventScrollReset: true,
      });
      scrollToFeed();
      return;
    }
    setMainTab('favorites');
    const nextParams = new URLSearchParams(searchParams);
    nextParams.set('tab', 'favorites');
    nextParams.delete('subtab');
    setSearchParams(nextParams, {
      replace: true,
      preventScrollReset: true,
    });
    scrollToFeed();
  };

  const handleMainTabChange = (key: string) => {
    const next = parseProfileMainTab(key);
    setMainTab(next);
    const nextParams = new URLSearchParams(searchParams);
    nextParams.set('tab', next);
    if (next !== 'posts') nextParams.delete('subtab');
    setSearchParams(nextParams, { replace: true, preventScrollReset: true });
  };

  const handlePostSubTabChange = (key: string) => {
    const next = parseProfileSubTab(key);
    setPostSubTab(next);
    const nextParams = new URLSearchParams(searchParams);
    nextParams.set('tab', 'posts');
    nextParams.set('subtab', next);
    setSearchParams(nextParams, { replace: true, preventScrollReset: true });
  };

  const handleOtherTabChange = (key: string) => {
    const next = parseOtherProfileTab(key);
    setOtherTab(next);
    const nextParams = new URLSearchParams(searchParams);
    nextParams.set('tab', next);
    nextParams.delete('subtab');
    setSearchParams(nextParams, { replace: true, preventScrollReset: true });
  };

  const handleBack = () => {
    if (window.history.length > 1) {
      navigate(-1);
      return;
    }
    navigate('/');
  };

  useEffect(() => {
    if (!isOther || !viewUser?.accountId) {
      setFollowed(false);
      setBlocked(false);
      setOtherTab('posts');
      return;
    }
    let cancelled = false;
    void Promise.all([
      checkFollowByAccountApi(viewUser.accountId),
      checkBlockByAccountApi(viewUser.accountId),
    ]).then(([followRes, isBlocked]) => {
      if (!cancelled) {
        setFollowed(Boolean(followRes.data?.followed));
        setBlocked(isBlocked);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [isOther, viewUser?.accountId]);

  const handleFollowOther = async () => {
    if (!requireLogin() || !viewUser?.accountId) return;
    try {
      const next = !followed;
      await toggleFollowByAccountApi(viewUser.accountId, next);
      setFollowed(next);
      message.success(next ? '已关注' : '已取消关注');
    } catch (err) {
      message.error(formatApiError('操作失败', err));
    }
  };

  const handleReportUser = () => {
    if (isSelf || !requireLogin() || !viewUser?.accountId) return;
    openReport('user', String(viewUser.accountId), '举报用户');
  };

  const handleBlockOther = () => {
    if (!requireLogin() || !viewUser?.accountId) return;
    const next = !blocked;
    const actionText = next ? '拉黑' : '取消拉黑';
    Modal.confirm({
      title: next ? '确认拉黑该用户？' : '确认取消拉黑？',
      content: next
        ? '拉黑后将无法查看对方内容，对方也无法与你互动。'
        : '取消拉黑后可恢复正常查看与互动。',
      okText: actionText,
      cancelText: '取消',
      okButtonProps: next ? { danger: true } : undefined,
      onOk: async () => {
        try {
          await toggleBlockByAccountApi(viewUser.accountId, next);
          setBlocked(next);
          if (next) {
            setFollowed(false);
          }
          message.success(next ? '已拉黑' : '已取消拉黑');
        } catch (err) {
          message.error(formatApiError(`${actionText}失败`, err));
        }
      },
    });
  };

  return (
    <div className="profile-page">
      <div className="profile-page__top-dock">
        <div className="profile-page__align-track">
          <PageSubTopBar
            onBack={handleBack}
            center={
              <div className="page-sub-top-bar__identity">
                <ProfileUserLink
                  accountId={heroAccountId}
                  nickname={heroName}
                  avatar={heroAvatar || undefined}
                  size={32}
                  showNickname={false}
                />
                <ProfileUserLink
                  accountId={heroAccountId}
                  nickname={viewLoading ? '加载中…' : heroName}
                  avatar={heroAvatar || undefined}
                  showAvatar={false}
                  size={0}
                  className="page-sub-top-bar__nickname"
                />
              </div>
            }
            extra={
              <div className="profile-page__top-extra">
                {isSelf ? (
                  <>
                    <Button size="small" onClick={() => navigate('/shop')}>
                      积分商城
                    </Button>
                    <Button
                      icon={<SafetyCertificateOutlined />}
                      size="small"
                      onClick={() => setSecurityOpen(true)}
                    >
                      账号与安全
                    </Button>
                  </>
                ) : (
                  <>
                    <FollowButton
                      followed={followed}
                      size="small"
                      onClick={() => void handleFollowOther()}
                    />
                    <Button
                      size="small"
                      icon={<StopOutlined />}
                      danger={blocked}
                      onClick={handleBlockOther}
                    >
                      {blocked ? '已拉黑' : '拉黑'}
                    </Button>
                    <Button
                      size="small"
                      icon={<FlagOutlined />}
                      onClick={handleReportUser}
                    >
                      举报
                    </Button>
                  </>
                )}
              </div>
            }
          />
        </div>
      </div>

      <section
        className={[
          'profile-hero',
          hasHeroBg ? 'profile-hero--decorated' : '',
          heroTextTheme === 'light' ? 'profile-hero--text-light' : '',
          heroTextTheme === 'dark' ? 'profile-hero--text-dark' : '',
        ]
          .filter(Boolean)
          .join(' ')}
      >
        {hasHeroBg ? (
          <ProfileBgBackdrop
            assetJson={heroBgAssetResolvedJson}
            cosmeticCode={heroDecoration?.profileBg?.code}
          />
        ) : null}
        <div className="profile-hero__inner">
          {viewLoading ? (
            <div className="profile-hero__loading">
              <Spin />
            </div>
          ) : isOther && !viewUser ? (
            <Empty description="用户不存在或暂不可见" />
          ) : (
            <>
              <div className="profile-hero__lead">
                <button
                  type="button"
                  className={`profile-hero__avatar-btn${avatarBusy ? ' is-busy' : ''}`}
                  onClick={() => isSelf && setAvatarOpen(true)}
                  aria-label={
                    avatarBusy
                      ? '头像审核中'
                      : isSelf
                        ? '点击修改头像'
                        : heroName
                  }
                  disabled={!isSelf}
                >
                  <span
                    className={`profile-hero__avatar-wrap${
                      hasHeroFrame ? ' profile-hero__avatar-wrap--framed' : ''
                    }`}
                  >
                    <UserAvatarWithFrame
                      accountId={heroAccountId}
                      name={heroName}
                      src={heroAvatar || undefined}
                      size={88}
                      className="profile-hero__avatar"
                    />
                    {isSelf ? (
                      <span className="profile-hero__avatar-mask">
                        <CameraOutlined />
                        <span>{avatarBusy ? '审核中' : '点击修改头像'}</span>
                      </span>
                    ) : null}
                  </span>
                  {isSelf ? auditTag(user?.avatarAuditStatus) : null}
                </button>
              </div>

              <div className="profile-hero__info">
                <div className="profile-hero__name-row">
                  {isSelf ? (
                    <button
                      type="button"
                      className={`profile-hero__text-hit${usernameBusy ? ' is-busy' : ''}`}
                      disabled={usernameBusy}
                      onClick={() => setUsernameOpen(true)}
                      aria-label="点击修改昵称"
                    >
                      <h1 className="profile-hero__name">{heroName}</h1>
                      {!usernameBusy && (
                        <span className="profile-hero__text-mask">
                          点击修改昵称
                        </span>
                      )}
                    </button>
                  ) : (
                    <h1 className="profile-hero__name">{heroName}</h1>
                  )}
                  {isSelf ? auditTag(user?.usernameAuditStatus) : null}
                  {isSelf ? (
                    <button
                      type="button"
                      className="profile-hero__edit-btn"
                      disabled={usernameBusy}
                      onClick={() => setUsernameOpen(true)}
                    >
                      <EditOutlined />
                      <span>修改昵称</span>
                    </button>
                  ) : null}
                </div>

                <Text type="secondary" className="profile-hero__account-id">
                  ID {heroAccountId ?? '—'}
                </Text>

                <div className="profile-hero__bio-row">
                  {isSelf ? (
                    <button
                      type="button"
                      className={`profile-hero__text-hit profile-hero__text-hit--bio${signatureBusy ? ' is-busy' : ''}`}
                      disabled={signatureBusy}
                      onClick={() => setSignatureOpen(true)}
                      aria-label="点击修改个性签名"
                    >
                      <Text type="secondary" className="profile-hero__bio">
                        {heroBio}
                      </Text>
                      {!signatureBusy && (
                        <span className="profile-hero__text-mask">
                          点击修改个性签名
                        </span>
                      )}
                    </button>
                  ) : (
                    <Text type="secondary" className="profile-hero__bio">
                      {heroBio}
                    </Text>
                  )}
                  {isSelf ? auditTag(user?.signatureAuditStatus) : null}
                  {isSelf ? (
                    <button
                      type="button"
                      className="profile-hero__edit-btn"
                      disabled={signatureBusy}
                      onClick={() => setSignatureOpen(true)}
                    >
                      <EditOutlined />
                      <span>修改签名</span>
                    </button>
                  ) : null}
                </div>

                {isSelf &&
                  (user?.usernameAuditMessage ||
                    user?.signatureAuditMessage ||
                    user?.avatarAuditMessage) && (
                    <Text type="danger" className="profile-hero__audit-msg">
                      {[
                        user.usernameAuditMessage &&
                          `昵称：${user.usernameAuditMessage}`,
                        user.signatureAuditMessage &&
                          `签名：${user.signatureAuditMessage}`,
                        user.avatarAuditMessage &&
                          `头像：${user.avatarAuditMessage}`,
                      ]
                        .filter(Boolean)
                        .join('；')}
                    </Text>
                  )}

                {isSelf ? (
                  <div className="profile-hero__stats">
                    {statItems.map((item) => (
                      <button
                        key={item.key}
                        type="button"
                        className="profile-hero__stat"
                        onClick={() => handleStatClick(item.key)}
                      >
                        <strong>{formatCount(item.value)}</strong>
                        <span>{item.label}</span>
                      </button>
                    ))}
                  </div>
                ) : (
                  <div className="profile-hero__stats profile-hero__stats--inline">
                    <div className="profile-hero__stat">
                      <strong>{formatCount(stats.following)}</strong>
                      <span>关注</span>
                    </div>
                    <div className="profile-hero__stat">
                      <strong>{formatCount(stats.followers)}</strong>
                      <span>粉丝</span>
                    </div>
                  </div>
                )}
              </div>
            </>
          )}
        </div>
      </section>

      <section className="profile-body">
        {isSelf ? (
          <>
            <SteamSection className="profile-body__steam" />

            <div ref={feedAnchorRef} className="profile-body__feed-anchor">
              <Tabs
                activeKey={mainTab}
                onChange={handleMainTabChange}
                items={mainTabItems}
                className="profile-main-tabs"
                size="large"
              />

              {mainTab === 'posts' && (
                <Tabs
                  activeKey={postSubTab}
                  onChange={handlePostSubTabChange}
                  items={postSubItems}
                  className="profile-sub-tabs"
                  size="small"
                />
              )}

              <ProfileFeed mainTab={mainTab} postSubTab={postSubTab} />
            </div>
          </>
        ) : viewUser ? (
          blocked ? (
            <Empty
              className="profile-body__other-empty"
              description="无法查看该用户内容"
            />
          ) : (
            <>
              <div ref={feedAnchorRef} className="profile-body__feed-anchor">
                <Tabs
                  activeKey={otherTab}
                  onChange={handleOtherTabChange}
                  items={otherTabItems}
                  className="profile-main-tabs"
                  size="large"
                />

                {otherTab === 'posts' ? (
                  <ProfileFeed
                    mainTab="posts"
                    postSubTab="published"
                    targetAccountId={viewUser.accountId}
                    authorUser={{
                      accountId: viewUser.accountId,
                      username: viewUser.username,
                      avatar: viewUser.avatar,
                    }}
                    isOther
                  />
                ) : (
                  <SteamSection
                    className="profile-body__steam"
                    readOnly
                    targetAccountId={viewUser.accountId}
                  />
                )}
              </div>
            </>
          )
        ) : null}
      </section>

      <EditUsernameModal
        open={usernameOpen}
        onClose={() => setUsernameOpen(false)}
      />
      <EditSignatureModal
        open={signatureOpen}
        onClose={() => setSignatureOpen(false)}
      />
      <AvatarViewerModal
        open={avatarOpen}
        onClose={() => setAvatarOpen(false)}
        displayAvatar={displayAvatar || undefined}
        displayName={username}
      />
      <AccountSecurityModal
        open={securityOpen}
        onClose={() => setSecurityOpen(false)}
      />
      <ProfileUserListModal
        open={userListOpen}
        type={userListType}
        onClose={() => setUserListOpen(false)}
      />
      {reportTarget ? (
        <ReportModal
          open={reportOpen}
          targetType={reportTarget.type}
          targetId={reportTarget.id}
          title={reportTarget.title}
          onClose={closeReport}
        />
      ) : null}
    </div>
  );
}

export default Profile;
