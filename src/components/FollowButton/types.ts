import type { ButtonProps } from 'antd';

export interface FollowButtonProps {
  followed?: boolean;
  onClick?: ButtonProps['onClick'];
  disabled?: boolean;
  size?: ButtonProps['size'];
  className?: string;
  followText?: string;
  followedText?: string;
}
