import type { MenuProps } from 'antd';
import type { ReactNode, Ref } from 'react';

import type { PostAuthor } from '@/types/post';

export interface AuthorHeaderProps {
  author: PostAuthor;
  time?: string;
  followed?: boolean;
  isOwner?: boolean;
  sentinelRef?: Ref<HTMLElement>;
  extra?: ReactNode;
  onFollow?: () => void;
  moreMenu?: MenuProps['items'];
  className?: string;
}
