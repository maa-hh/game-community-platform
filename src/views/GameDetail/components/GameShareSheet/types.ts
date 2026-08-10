import type { ReactNode } from 'react';
import type { IGameDetail } from '@/types/game';

export interface IGameShareSheetProps {
  open: boolean;
  detail: IGameDetail;
  priceText?: string | null;
  onClose: () => void;
  onReposted?: (newPostId: string) => void;
}

export interface GameShareActionItem {
  key: 'repost' | 'steam' | 'detail';
  icon: ReactNode;
  label: string;
  desc: string;
  disabled?: boolean;
  onClick: () => void;
}
