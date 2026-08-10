import type { ReactNode } from 'react';

import type { PostCoverSource } from '@/utils/postCover';

export interface PostRowPreviewData extends PostCoverSource {
  createdAt?: string;
}

export interface PostRowPreviewProps {
  data: PostRowPreviewData;
  className?: string;
  onClick?: () => void;
  /** 封面左上角排名角标 */
  rank?: number;
  /** 标题下方扩展区（作者、统计等） */
  metaExtra?: ReactNode;
  /** 时间下方扩展区（个人页数据行等） */
  metaFooter?: ReactNode;
  /** 是否展示发布时间，默认 true */
  showTime?: boolean;
}
