export interface PostOwnerLinkItem {
  key: string;
  label: string;
  danger?: boolean;
  onClick: () => void;
}

export interface IProps {
  items: PostOwnerLinkItem[];
  className?: string;
}
