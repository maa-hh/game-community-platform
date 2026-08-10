import { useInsertionEffect, useLayoutEffect, useMemo, useRef } from 'react';
import { useLocation, useNavigationType } from 'react-router-dom';

interface ScrollPosition {
  x: number;
  y: number;
}

interface RestoreState {
  restoreScrollY?: unknown;
}

const STORAGE_PREFIX = 'gc:scroll-position:';
const RESTORING_CLASS = 'is-restoring-scroll';
const RESTORE_TIMEOUT_MS = 10_000;

function storageKey(locationKey: string) {
  return `${STORAGE_PREFIX}${locationKey}`;
}

function readPosition(locationKey: string): ScrollPosition | undefined {
  try {
    const raw = sessionStorage.getItem(storageKey(locationKey));
    if (!raw) return undefined;
    const parsed = JSON.parse(raw) as Partial<ScrollPosition>;
    if (!Number.isFinite(parsed.x) || !Number.isFinite(parsed.y)) {
      return undefined;
    }
    return { x: Number(parsed.x), y: Number(parsed.y) };
  } catch {
    return undefined;
  }
}

function savePosition(locationKey: string) {
  try {
    sessionStorage.setItem(
      storageKey(locationKey),
      JSON.stringify({ x: window.scrollX, y: window.scrollY }),
    );
  } catch {
    // sessionStorage 不可用时不影响页面导航。
  }
}

function readStateRestoreY(state: unknown) {
  if (typeof state !== 'object' || state === null) return null;
  const value = (state as RestoreState).restoreScrollY;
  return typeof value === 'number' && Number.isFinite(value) && value >= 0
    ? value
    : null;
}

/**
 * 基于 history entry key 统一恢复全站滚动位置。
 *
 * POP 返回不依赖页面白名单或业务组件传参；异步页面会在内容高度足够时恢复，
 * 恢复期间隐藏路由内容，避免先显示顶部、随后跳回原位置的闪烁。
 */
export function useScrollRestoration() {
  const location = useLocation();
  const navigationType = useNavigationType();
  const locationKey = location.key || 'default';
  const entryKey = `${locationKey}:${location.pathname}${location.search}`;
  const activeEntryKeyRef = useRef(entryKey);
  const stateRestoreY = readStateRestoreY(location.state);
  const restorePosition = useMemo(() => {
    if (stateRestoreY != null) {
      return { x: window.scrollX, y: stateRestoreY };
    }
    return navigationType === 'POP' ? readPosition(entryKey) : undefined;
  }, [entryKey, navigationType, stateRestoreY]);

  // 必须在事件回调可能触发的 scroll 之前切换归属，避免顶部位置覆盖上一页。
  activeEntryKeyRef.current = entryKey;

  // 在浏览器绘制新路由前隐藏待恢复内容；同步恢复成功时会在同一帧移除。
  useInsertionEffect(() => {
    if (!restorePosition || restorePosition.y <= 0) return undefined;
    document.documentElement.classList.add(RESTORING_CLASS);
    return () => {
      document.documentElement.classList.remove(RESTORING_CLASS);
    };
  }, [entryKey, restorePosition]);

  useLayoutEffect(() => {
    const previous = window.history.scrollRestoration;
    window.history.scrollRestoration = 'manual';
    return () => {
      window.history.scrollRestoration = previous;
    };
  }, []);

  // scroll 持续记录当前 history entry；pointerdown/click 保证导航前已落盘。
  useLayoutEffect(() => {
    const save = () => savePosition(activeEntryKeyRef.current);
    window.addEventListener('scroll', save, { passive: true });
    document.addEventListener('pointerdown', save, true);
    document.addEventListener('click', save, true);
    window.addEventListener('pagehide', save);
    save();
    return () => {
      window.removeEventListener('scroll', save);
      document.removeEventListener('pointerdown', save, true);
      document.removeEventListener('click', save, true);
      window.removeEventListener('pagehide', save);
    };
  }, []);

  useLayoutEffect(() => {
    if (!restorePosition) {
      document.documentElement.classList.remove(RESTORING_CLASS);
      if (navigationType === 'PUSH') {
        window.scrollTo({ top: 0, left: 0, behavior: 'auto' });
      }
      return undefined;
    }

    let finished = false;
    let resizeObserver: ResizeObserver | undefined;
    let mutationObserver: MutationObserver | undefined;
    let timeoutId: number | undefined;
    let scheduledFrame: number | undefined;
    let revealFrame: number | undefined;

    const cleanup = () => {
      resizeObserver?.disconnect();
      mutationObserver?.disconnect();
      if (timeoutId != null) window.clearTimeout(timeoutId);
      if (scheduledFrame != null) {
        window.cancelAnimationFrame(scheduledFrame);
      }
      if (revealFrame != null) window.cancelAnimationFrame(revealFrame);
      window.removeEventListener('wheel', cancelRestore);
      window.removeEventListener('touchstart', cancelRestore);
      window.removeEventListener('keydown', cancelRestore);
    };

    const finish = (top: number) => {
      if (finished) return;
      finished = true;
      window.scrollTo({
        top,
        left: restorePosition.x,
        behavior: 'auto',
      });
      resizeObserver?.disconnect();
      mutationObserver?.disconnect();
      if (timeoutId != null) window.clearTimeout(timeoutId);
      // 合成滚动可能晚于 React 提交；下一帧再次校准后再显示页面。
      revealFrame = window.requestAnimationFrame(() => {
        window.scrollTo({
          top,
          left: restorePosition.x,
          behavior: 'auto',
        });
        document.documentElement.classList.remove(RESTORING_CLASS);
        cleanup();
      });
    };

    function cancelRestore() {
      if (finished) return;
      finished = true;
      document.documentElement.classList.remove(RESTORING_CLASS);
      cleanup();
    }

    const tryRestore = () => {
      scheduledFrame = undefined;
      if (finished) return;
      const maxY = Math.max(
        0,
        document.documentElement.scrollHeight - window.innerHeight,
      );
      if (maxY + 1 >= restorePosition.y) {
        finish(restorePosition.y);
      }
    };

    const scheduleRestore = () => {
      if (finished || scheduledFrame != null) return;
      scheduledFrame = window.requestAnimationFrame(tryRestore);
    };

    tryRestore();
    if (!finished) {
      const observedContent =
        document.querySelector('.main-layout__route-view--active') ??
        document.querySelector('.site-main') ??
        document.body;
      if (typeof ResizeObserver !== 'undefined') {
        resizeObserver = new ResizeObserver(scheduleRestore);
        resizeObserver.observe(observedContent);
      }
      mutationObserver = new MutationObserver(scheduleRestore);
      mutationObserver.observe(observedContent, {
        childList: true,
        subtree: true,
        attributes: true,
      });
      timeoutId = window.setTimeout(() => {
        const maxY = Math.max(
          0,
          document.documentElement.scrollHeight - window.innerHeight,
        );
        finish(Math.min(restorePosition.y, maxY));
      }, RESTORE_TIMEOUT_MS);
      window.addEventListener('wheel', cancelRestore, { passive: true });
      window.addEventListener('touchstart', cancelRestore, { passive: true });
      window.addEventListener('keydown', cancelRestore);
    }

    return () => {
      finished = true;
      cleanup();
      document.documentElement.classList.remove(RESTORING_CLASS);
    };
  }, [entryKey, navigationType, restorePosition]);
}
