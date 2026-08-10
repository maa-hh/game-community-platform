import type { ReactNode } from 'react';

export interface IProps {
  tip: string;
  /** 插槽：显示在 tip 下方的自定义内容 */
  children?: ReactNode;
}
