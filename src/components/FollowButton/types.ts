import type { ButtonProps } from 'antd';

export interface FollowButtonProps {
  followed?: boolean;
  onClick?: () => void;
  size?: ButtonProps['size'];
  className?: string;
}
