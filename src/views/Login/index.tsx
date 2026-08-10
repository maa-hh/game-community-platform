import React, { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Button, Modal } from 'antd';
import { CaretRightOutlined } from '@ant-design/icons';

import { useAuthModal } from '@/hooks/useAuthModal';
import { useTheme } from '@/hooks/useTheme';
import { isAuthenticated } from '@/utils/storage';
import type { ILoginLocationState } from '@/components/auth/constants';
import {
  BRAND_NAME,
  BRAND_SLOGAN,
  HERO_POSTER_SRC,
  HERO_VIDEO_FULL_SRC,
  HERO_VIDEO_SRC,
} from '@/constants/brand';

import './style.less';

function Login() {
  const navigate = useNavigate();
  const location = useLocation();
  const { openAuth } = useAuthModal();
  const { setRouteOverride } = useTheme();
  const bgVideoRef = useRef<HTMLVideoElement>(null);
  const [showFullVideo, setShowFullVideo] = useState(false);
  const autoOpenedRef = useRef(false);

  // 仅登录落地页临时深色（不改写站内默认白天偏好）
  useEffect(() => {
    setRouteOverride('dark');
    return () => setRouteOverride(null);
  }, [setRouteOverride]);

  // 鉴权跳转过来时自动打开登录弹窗（已登录则不弹）
  useEffect(() => {
    if (autoOpenedRef.current || isAuthenticated()) return;
    const state = location.state as ILoginLocationState | null;
    if (state?.tip || state?.from) {
      autoOpenedRef.current = true;
      openAuth('login');
    }
  }, [location.state, openAuth]);

  const loggedIn = isAuthenticated();

  useEffect(() => {
    if (showFullVideo) {
      bgVideoRef.current?.pause();
    } else {
      bgVideoRef.current?.play().catch(() => undefined);
    }
  }, [showFullVideo]);

  return (
    <div className="login-landing">
      <div className="login-landing__video-box">
        <video
          ref={bgVideoRef}
          className="login-landing__bg-video"
          src={HERO_VIDEO_SRC}
          poster={HERO_POSTER_SRC}
          autoPlay
          muted
          loop
          playsInline
        />
        <div className="login-landing__mask" />
      </div>

      <div className="login-landing__content">
        <p className="login-landing__eyebrow">高能玩家聚集地</p>
        <h1 className="login-landing__title">{BRAND_NAME}</h1>
        <p className="login-landing__slogan">{BRAND_SLOGAN}</p>
        <p className="login-landing__desc">
          发现游戏、分享攻略、结识同好。加入社区，开启你的下一场冒险。
        </p>

        <div className="login-landing__actions">
          {loggedIn ? (
            <Button
              type="primary"
              size="large"
              className="login-landing__cta"
              onClick={() => navigate('/')}
            >
              进入社区
            </Button>
          ) : (
            <Button
              type="primary"
              size="large"
              className="login-landing__cta"
              onClick={() => openAuth('login')}
            >
              立即登录
            </Button>
          )}
          <Button
            size="large"
            ghost
            className="login-landing__play"
            icon={<CaretRightOutlined />}
            onClick={() => setShowFullVideo(true)}
          >
            观看宣传片
          </Button>
        </div>
      </div>

      <Modal
        open={showFullVideo}
        onCancel={() => setShowFullVideo(false)}
        footer={null}
        centered
        width={960}
        destroyOnClose
        className="login-landing__video-modal"
        styles={{ body: { padding: 0, lineHeight: 0 } }}
      >
        <video
          className="login-landing__full-video"
          src={HERO_VIDEO_FULL_SRC}
          poster={HERO_POSTER_SRC}
          controls
          autoPlay
        />
      </Modal>
    </div>
  );
}

export default Login;
