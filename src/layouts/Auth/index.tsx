import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Outlet } from 'react-router-dom';
import styled from 'styled-components';
import {
  TeamOutlined,
  TrophyOutlined,
  CommentOutlined,
  LeftOutlined,
  RightOutlined,
} from '@ant-design/icons';

const MIN_LEFT_PERCENT = 28;
const MAX_LEFT_PERCENT = 72;
const DEFAULT_LEFT_PERCENT = 52;
const EXPAND_FORM_LEFT_PERCENT = MIN_LEFT_PERCENT;

const EXPAND_INTRO_LEFT_PERCENT = MAX_LEFT_PERCENT;

const AuthPage = styled.div<{ $dragging: boolean }>`
  display: flex;
  min-height: 100vh;
  background: #f0f5ff;
  user-select: ${(props) => (props.$dragging ? 'none' : 'auto')};
`;

const IntroPanel = styled.div<{ $animate: boolean }>`
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 64px 56px;
  background: linear-gradient(135deg, #1677ff 0%, #0958d9 50%, #003a8c 100%);
  color: #fff;
  overflow: hidden;
  min-width: 280px;
  cursor: pointer;
  transition: ${(props) => (props.$animate ? 'width 0.35s ease' : 'none')};

  @media (max-width: 768px) {
    display: none;
  }
`;

/** 细分割线 + 两侧 U 形把手按钮（同一水平线、垂直居中） */
const SplitterBar = styled.div<{ $animate: boolean }>`
  position: relative;
  z-index: 2;
  width: 4px;
  flex-shrink: 0;
  cursor: col-resize;
  background: #d6e4ff;
  transition: ${(props) => (props.$animate ? 'background 0.2s' : 'none')};

  &:hover,
  &.dragging {
    background: #91caff;
  }

  @media (max-width: 768px) {
    display: none;
  }
`;

const SplitterControls = styled.div`
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  display: flex;
  align-items: center;
  gap: 0;
  pointer-events: none;
`;

/**
 * U 形把手：贴分割线一侧为平口，外侧为半圆弧
 * 左按钮：把右侧往左拉（扩大表单区）
 * 右按钮：把左侧往右拉（扩大介绍区）
 */
const SplitterBtn = styled.button<{
  $side: 'left' | 'right';
  $active?: boolean;
}>`
  pointer-events: auto;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 64px;
  padding: 0;
  border: 1.5px solid ${(props) => (props.$active ? '#1677ff' : '#91caff')};
  cursor: pointer;
  color: ${(props) => (props.$active ? '#fff' : '#1677ff')};
  background: ${(props) =>
    props.$active ? '#1677ff' : 'rgba(255, 255, 255, 0.98)'};
  box-shadow: 0 2px 10px rgba(22, 119, 255, 0.14);
  transition:
    background 0.2s,
    color 0.2s,
    border-color 0.2s,
    box-shadow 0.2s;

  ${(props) =>
    props.$side === 'left'
      ? `
    /* 左侧半圆 U：开口贴分割线 */
    border-right: none;
    border-radius: 32px 0 0 32px;
  `
      : `
    /* 右侧半圆 U：开口贴分割线 */
    border-left: none;
    border-radius: 0 32px 32px 0;
  `}

  .anticon {
    font-size: 14px;
  }

  &:hover {
    color: #fff;
    background: #4096ff;
    border-color: #4096ff;
    box-shadow: 0 3px 12px rgba(22, 119, 255, 0.24);
  }

  &:active {
    background: #0958d9;
    border-color: #0958d9;
  }
`;

const IntroTitle = styled.h1`
  margin: 0 0 16px;
  font-size: 36px;
  font-weight: 700;
  line-height: 1.3;
`;

const IntroDesc = styled.p`
  margin: 0 0 40px;
  font-size: 16px;
  line-height: 1.8;
  opacity: 0.88;
  max-width: 420px;
`;

const FeatureList = styled.ul`
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 20px;
`;

const FeatureItem = styled.li`
  display: flex;
  align-items: flex-start;
  gap: 14px;
  font-size: 15px;
  line-height: 1.6;

  .anticon {
    font-size: 22px;
    margin-top: 2px;
    opacity: 0.9;
  }
`;

const FormPanel = styled.div<{ $animate: boolean; $expanded: boolean }>`
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 32px;
  background: #fff;
  min-width: 320px;
  flex: 1;
  overflow-y: auto;
  cursor: pointer;
  transition: ${(props) => (props.$animate ? 'width 0.35s ease' : 'none')};
  box-shadow: ${(props) =>
    props.$expanded ? '-8px 0 24px rgba(22, 119, 255, 0.08)' : 'none'};

  @media (max-width: 768px) {
    min-width: 0;
    cursor: default;
    box-shadow: none;
  }
`;

const FormWrapper = styled.div`
  width: 100%;
  max-width: 360px;
`;

function AuthLayout() {
  const [leftPercent, setLeftPercent] = useState(DEFAULT_LEFT_PERCENT);
  const [dragging, setDragging] = useState(false);
  const pageRef = useRef<HTMLDivElement>(null);

  const formExpanded = leftPercent <= EXPAND_FORM_LEFT_PERCENT + 1;
  const introExpanded = leftPercent >= EXPAND_INTRO_LEFT_PERCENT - 1;

  const expandIntroPanel = useCallback(() => {
    setLeftPercent(EXPAND_INTRO_LEFT_PERCENT);
  }, []);

  const expandFormPanel = useCallback(() => {
    setLeftPercent(EXPAND_FORM_LEFT_PERCENT);
  }, []);

  const handleMouseDown = useCallback((e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    setDragging(true);
  }, []);

  const handleExpandIntroClick = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      expandIntroPanel();
    },
    [expandIntroPanel],
  );

  const handleExpandFormClick = useCallback(
    (e: React.MouseEvent) => {
      e.stopPropagation();
      expandFormPanel();
    },
    [expandFormPanel],
  );

  useEffect(() => {
    if (!dragging) return;

    const handleMouseMove = (e: MouseEvent) => {
      const pageWidth = pageRef.current?.offsetWidth ?? window.innerWidth;
      const percent = (e.clientX / pageWidth) * 100;
      const clamped = Math.min(
        MAX_LEFT_PERCENT,
        Math.max(MIN_LEFT_PERCENT, percent),
      );
      setLeftPercent(clamped);
    };

    const handleMouseUp = () => {
      setDragging(false);
    };

    window.addEventListener('mousemove', handleMouseMove);
    window.addEventListener('mouseup', handleMouseUp);

    return () => {
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('mouseup', handleMouseUp);
    };
  }, [dragging]);

  return (
    <AuthPage ref={pageRef} $dragging={dragging}>
      <IntroPanel
        style={{ width: `${leftPercent}%` }}
        $animate={!dragging}
        onClick={expandIntroPanel}
      >
        <IntroTitle>游戏社区</IntroTitle>
        <IntroDesc>
          发现好游戏、分享攻略心得、与玩家一起讨论。加入社区，开启你的游戏之旅。
        </IntroDesc>
        <FeatureList>
          <FeatureItem>
            <TrophyOutlined />
            <span>热门游戏评分与推荐，帮你快速找到下一款想玩的游戏</span>
          </FeatureItem>
          <FeatureItem>
            <CommentOutlined />
            <span>攻略、评测、讨论区，和志同道合的玩家交流</span>
          </FeatureItem>
          <FeatureItem>
            <TeamOutlined />
            <span>关注好友动态，组队开黑，共建活跃社区</span>
          </FeatureItem>
        </FeatureList>
      </IntroPanel>

      <SplitterBar
        className={dragging ? 'dragging' : ''}
        $animate={!dragging}
        onMouseDown={handleMouseDown}
        role="separator"
        aria-orientation="vertical"
        aria-label="拖动调整宽度"
      >
        <SplitterControls>
          {/* 左按钮：把右侧往左拉 → 扩大表单区 */}
          <SplitterBtn
            type="button"
            $side="left"
            $active={formExpanded}
            aria-label="把右侧往左拉，扩大表单区"
            onClick={handleExpandFormClick}
            onMouseDown={(e) => e.stopPropagation()}
          >
            <LeftOutlined />
          </SplitterBtn>
          {/* 右按钮：把左侧往右拉 → 扩大介绍区 */}
          <SplitterBtn
            type="button"
            $side="right"
            $active={introExpanded}
            aria-label="把左侧往右拉，扩大介绍区"
            onClick={handleExpandIntroClick}
            onMouseDown={(e) => e.stopPropagation()}
          >
            <RightOutlined />
          </SplitterBtn>
        </SplitterControls>
      </SplitterBar>

      <FormPanel
        style={{ width: `${100 - leftPercent}%` }}
        $animate={!dragging}
        $expanded={formExpanded}
        onClick={expandFormPanel}
      >
        <FormWrapper>
          <Outlet />
        </FormWrapper>
      </FormPanel>
    </AuthPage>
  );
}

export default AuthLayout;
