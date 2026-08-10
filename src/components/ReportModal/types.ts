export type ReportTargetType =
  'article' | 'comment' | 'reply' | 'user' | 'danmaku';

export interface ReportModalProps {
  open: boolean;
  targetType: ReportTargetType;
  targetId: string;
  title?: string;
  onClose: () => void;
  onSuccess?: () => void;
}
