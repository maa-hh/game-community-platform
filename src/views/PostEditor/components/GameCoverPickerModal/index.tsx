import { useEffect, useMemo, useState } from 'react';
import { Empty, Modal, Spin, message } from 'antd';
import { CheckOutlined } from '@ant-design/icons';

import { fetchGameDetailApi } from '@/service/game';
import type { IGameTag } from '@/types/game';
import {
  collectGameCoverOptions,
  type IGameCoverOption,
} from '@/utils/gameCoverOptions';

import './style.less';

export interface IGameCoverPickerModalProps {
  open: boolean;
  gameAppIds: number[];
  gameOptions: IGameTag[];
  remainingSlots: number;
  existingUrls: string[];
  onCancel: () => void;
  onConfirm: (options: IGameCoverOption[]) => void;
}

export default function GameCoverPickerModal({
  open,
  gameAppIds,
  gameOptions,
  remainingSlots,
  existingUrls,
  onCancel,
  onConfirm,
}: IGameCoverPickerModalProps) {
  const [loading, setLoading] = useState(false);
  const [candidates, setCandidates] = useState<IGameCoverOption[]>([]);
  const [selected, setSelected] = useState<string[]>([]);
  const [failedUrls, setFailedUrls] = useState<Set<string>>(new Set());

  const existingSet = useMemo(
    () => new Set(existingUrls.map((url) => url.trim())),
    [existingUrls],
  );

  useEffect(() => {
    if (!open) {
      setSelected([]);
      setFailedUrls(new Set());
      return;
    }

    setFailedUrls(new Set());

    if (gameAppIds.length === 0) {
      setCandidates([]);
      return;
    }

    let cancelled = false;
    setLoading(true);
    void Promise.all(
      gameAppIds.map(async (appId) => {
        try {
          const res = await fetchGameDetailApi(appId);
          if (!res.data) return [] as IGameCoverOption[];
          return collectGameCoverOptions(res.data);
        } catch {
          const fallback = gameOptions.find((item) => item.appId === appId);
          if (!fallback?.iconUrl) return [] as IGameCoverOption[];
          return [
            {
              appId,
              gameName: fallback.name,
              url: fallback.iconUrl,
              label: '头图',
            },
          ];
        }
      }),
    )
      .then((groups) => {
        if (cancelled) return;
        const merged: IGameCoverOption[] = [];
        const seen = new Set<string>();
        groups.flat().forEach((item) => {
          if (seen.has(item.url)) return;
          seen.add(item.url);
          merged.push(item);
        });
        setCandidates(merged);
      })
      .catch(() => {
        if (!cancelled) {
          message.error('加载游戏封面失败');
          setCandidates([]);
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [gameAppIds, gameOptions, open]);

  const visibleCandidates = candidates.filter(
    (item) => !failedUrls.has(item.url),
  );

  const handleImageError = (url: string) => {
    setFailedUrls((prev) => {
      const next = new Set(prev);
      next.add(url);
      return next;
    });
    setSelected((prev) => prev.filter((item) => item !== url));
  };

  const toggleSelect = (url: string) => {
    if (existingSet.has(url)) return;
    setSelected((prev) => {
      if (prev.includes(url)) {
        return prev.filter((item) => item !== url);
      }
      if (prev.length >= remainingSlots) {
        message.warning(`最多还能添加 ${remainingSlots} 张封面`);
        return prev;
      }
      return [...prev, url];
    });
  };

  const handleOk = () => {
    const picked = candidates.filter((item) => selected.includes(item.url));
    if (picked.length === 0) {
      message.warning('请至少选择一张游戏封面');
      return;
    }
    onConfirm(picked);
    setSelected([]);
  };

  return (
    <Modal
      title="从关联游戏选择封面"
      open={open}
      onCancel={onCancel}
      onOk={handleOk}
      okText={`添加${selected.length > 0 ? `（${selected.length}）` : ''}`}
      cancelText="取消"
      width={720}
      destroyOnHidden
      className="game-cover-picker-modal"
    >
      <p className="game-cover-picker-modal__tip">
        可多选，将与手动上传的封面共存；首张将作为列表主封面。
        {remainingSlots > 0
          ? ` 还可添加 ${remainingSlots} 张。`
          : ' 已达上限。'}
      </p>

      {loading ? (
        <div className="game-cover-picker-modal__loading">
          <Spin />
        </div>
      ) : visibleCandidates.length === 0 ? (
        <Empty description="暂无可选封面，请先关联游戏" />
      ) : (
        <div className="game-cover-picker-modal__grid">
          {visibleCandidates.map((item) => {
            const used = existingSet.has(item.url);
            const active = selected.includes(item.url);
            return (
              <button
                key={`${item.appId}-${item.url}`}
                type="button"
                className={`game-cover-picker-modal__item${
                  active ? ' game-cover-picker-modal__item--active' : ''
                }${used ? ' game-cover-picker-modal__item--used' : ''}`}
                onClick={() => toggleSelect(item.url)}
                disabled={used}
              >
                <img
                  src={item.url}
                  alt={item.label}
                  loading="lazy"
                  onError={() => handleImageError(item.url)}
                />
                <span className="game-cover-picker-modal__label">
                  {item.gameName} · {item.label}
                </span>
                {active ? (
                  <span className="game-cover-picker-modal__check" aria-hidden>
                    <CheckOutlined />
                  </span>
                ) : null}
                {used ? (
                  <span className="game-cover-picker-modal__used">已添加</span>
                ) : null}
              </button>
            );
          })}
        </div>
      )}
    </Modal>
  );
}
