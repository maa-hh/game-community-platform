/** 字段审核状态：0空闲 1审核中 2人工复核 */
export type FieldAuditStatus = 0 | 1 | 2;

export const FIELD_AUDIT = {
  NONE: 0 as FieldAuditStatus,
  AUDITING: 1 as FieldAuditStatus,
  HUMAN_REVIEW: 2 as FieldAuditStatus,
};

/** 通知事件：资料审核 */
export const PROFILE_AUDIT_EVENT = {
  PASSED: 11,
  REJECTED: 12,
  HUMAN_REVIEW: 13,
} as const;

// 通用 API 响应结构
export interface IDataType<T = unknown> {
  code: number;
  data: T;
  message: string;
}

// Banner 数据类型
export interface IBanner {
  id: number;
  title: string;
  imageUrl: string;
  link: string;
}

// 游戏列表项
export interface IGameItem {
  id: number;
  name: string;
  cover: string;
  rating: number;
  tags: string[];
}

// 推荐页数据
export interface IRecommendData {
  banners: IBanner[];
  hotGames: IGameItem[];
}

/**
 * 用户信息（登录 LoginUserVO + /user/me UserMeVO）。
 * 对外只使用 accountId；t_user.id 不进入前端模型。
 */
export interface IUserInfo {
  /** 对外展示的账号 ID，不等同于 t_user.id。 */
  accountId: number;
  /** 资料乐观锁版本。 */
  version?: number;
  email?: string;
  username: string;
  avatar?: string;
  signature?: string;
  steamAccount?: string | null;
  status?: number;
  type?: number;
  pendingUsername?: string | null;
  pendingSignature?: string | null;
  pendingAvatarUrl?: string | null;
  usernameAuditStatus?: FieldAuditStatus;
  signatureAuditStatus?: FieldAuditStatus;
  avatarAuditStatus?: FieldAuditStatus;
  usernameAuditMessage?: string | null;
  signatureAuditMessage?: string | null;
  avatarAuditMessage?: string | null;
  followCount?: number;
  fansCount?: number;
  banUntil?: string | null;
  banReason?: string | null;
}

/** 批量名片 / 搜索卡片（UserCardVO，对外仅 accountId） */
export interface IUserCard {
  accountId: number;
  username: string;
  avatar?: string;
  signature?: string;
}

// 登录参数
export interface ILoginParams {
  email: string;
  password: string;
}

// 注册参数
export interface IRegisterParams {
  email: string;
  password: string;
  code: string;
}

// 发送验证码参数（仅 /user/auth/send-code：注册、找回密码）
export interface ISendCodeParams {
  email: string;
  /** REGISTER | RESET_PASSWORD */
  bizType?: SendCodeBizType;
}

export type SendCodeBizType = 'REGISTER' | 'RESET_PASSWORD';

/** 修改密码 */
export interface IChangePasswordParams {
  oldPassword: string;
  newPassword: string;
  confirmPassword: string;
}

/** 改邮箱第二步 */
export interface IPrepareChangeEmailParams {
  oldCode: string;
  newEmail: string;
}

/** 改邮箱第三步 */
export interface IConfirmChangeEmailParams {
  oldCode: string;
  newEmail: string;
  newCode: string;
}

/** 注销账号（验证码校验当前邮箱） */
export interface ICancelAccountParams {
  code: string;
}

/** 用户搜索结果项（UserCardVO） */
export interface IUserSearchItem {
  accountId: number;
  username: string;
  avatar?: string;
  signature?: string;
}

/** 分页响应（与后端 PageResult 对齐） */
export interface IPageResult<T = unknown> {
  code: number;
  data: T[];
  message: string;
  page: number;
  size: number;
  total: number;
  /** 后端仍在异步补齐数据时为 true。 */
  expanding?: boolean;
}

// 发送验证码返回
export interface ISendCodeResult {
  expireIn: number;
}

// 登录成功返回（refresh 只通过 HttpOnly Cookie 下发，不在 JSON 里）
export interface IAuthResult {
  accessToken: string;
  /** accessToken 有效秒数 */
  accessExpiresIn: number;
  user: IUserInfo;
}

/** 注册成功（仅创建账号，不含 token；随后需再调登录） */
export interface IRegisterResult {
  email: string;
}

/** 找回密码参数 */
export interface IResetPasswordParams {
  email: string;
  password: string;
  code: string;
}

/** 刷新 token 返回（同样只返回新 access；新 refresh 写 Cookie） */
export interface IRefreshResult {
  accessToken: string;
  accessExpiresIn: number;
}

/** 把登录/me 响应归一成 IUserInfo；登录可缺 pending/audit，用 fallback 或 NONE */
export function normalizeUserInfo(
  raw: Partial<IUserInfo> & {
    accountId: number;
    nickname?: string | null;
    avatarUrl?: string | null;
  },
  fallback?: Partial<IUserInfo> | null,
): IUserInfo {
  const username =
    raw.username ||
    raw.nickname ||
    fallback?.username ||
    raw.email?.split('@')[0] ||
    fallback?.email?.split('@')[0] ||
    '玩家';
  return {
    accountId: raw.accountId,
    version: raw.version ?? fallback?.version,
    email: raw.email ?? fallback?.email,
    username,
    avatar: raw.avatar ?? raw.avatarUrl ?? fallback?.avatar,
    signature: raw.signature ?? fallback?.signature,
    steamAccount: raw.steamAccount ?? fallback?.steamAccount ?? null,
    status: raw.status ?? fallback?.status,
    type: raw.type ?? fallback?.type,
    pendingUsername: raw.pendingUsername ?? fallback?.pendingUsername ?? null,
    pendingSignature:
      raw.pendingSignature ?? fallback?.pendingSignature ?? null,
    pendingAvatarUrl:
      raw.pendingAvatarUrl ?? fallback?.pendingAvatarUrl ?? null,
    usernameAuditStatus:
      raw.usernameAuditStatus ??
      fallback?.usernameAuditStatus ??
      FIELD_AUDIT.NONE,
    signatureAuditStatus:
      raw.signatureAuditStatus ??
      fallback?.signatureAuditStatus ??
      FIELD_AUDIT.NONE,
    avatarAuditStatus:
      raw.avatarAuditStatus ?? fallback?.avatarAuditStatus ?? FIELD_AUDIT.NONE,
    usernameAuditMessage:
      raw.usernameAuditMessage ?? fallback?.usernameAuditMessage ?? null,
    signatureAuditMessage:
      raw.signatureAuditMessage ?? fallback?.signatureAuditMessage ?? null,
    avatarAuditMessage:
      raw.avatarAuditMessage ?? fallback?.avatarAuditMessage ?? null,
    followCount: raw.followCount ?? fallback?.followCount,
    fansCount: raw.fansCount ?? fallback?.fansCount,
    banUntil: raw.banUntil ?? fallback?.banUntil ?? null,
    banReason: raw.banReason ?? fallback?.banReason ?? null,
  };
}

export function isFieldBusy(status?: FieldAuditStatus | null): boolean {
  return status === FIELD_AUDIT.AUDITING || status === FIELD_AUDIT.HUMAN_REVIEW;
}
