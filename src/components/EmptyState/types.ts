import type { ReactNode } from 'react';

export interface EmptyStateProps {
  description?: ReactNode;
  actionText?: string;
  onAction?: () => void;
  className?: string;
}
