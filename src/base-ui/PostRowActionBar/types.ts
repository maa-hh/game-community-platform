export interface PostRowActionItem {
  key: string;
  label: string;
  danger?: boolean;
  onClick: () => void;
}

export interface PostRowActionBarProps {
  items: PostRowActionItem[];
  className?: string;
}
