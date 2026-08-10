export interface ISteamSectionProps {
  className?: string;
  /** 他人主页：目标用户对外 accountId */
  targetAccountId?: number;
  /** 只读模式（隐藏绑定/同步等操作） */
  readOnly?: boolean;
}
