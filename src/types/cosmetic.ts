export type CosmeticSlot =
  'AVATAR_FRAME' | 'COMMENT_CARD' | 'COMMENT_FONT' | 'POST_CARD' | 'PROFILE_BG';

export type CosmeticEffectMode = 'EQUIP' | 'CONSUMABLE';
export type CosmeticItemState = 'ACTIVE' | 'EXPIRED';

export interface ICosmeticEquipped {
  code?: string;
  name?: string;
  category?: string;
  assetJson?: string | Record<string, unknown>;
}

export interface IUserDecoration {
  accountId?: number;
  avatarFrame?: ICosmeticEquipped;
  commentCard?: ICosmeticEquipped;
  commentFont?: ICosmeticEquipped;
  postCard?: ICosmeticEquipped;
  profileBg?: ICosmeticEquipped;
  activeEffects?: {
    effectCode?: string;
    sourceCosmeticCode?: string;
    expireAt?: string;
    payloadJson?: string;
  }[];
}

export interface IUserCosmeticItem {
  cosmeticCode: string;
  name?: string;
  category?: string;
  effectMode?: CosmeticEffectMode;
  slot?: CosmeticSlot;
  previewUrl?: string;
  assetJson?: string | Record<string, unknown>;
  quantity?: number;
  equipped?: boolean;
  canUse?: boolean;
  state?: CosmeticItemState;
  acquiredAt?: string;
  expireAt?: string;
}

export interface IAvatarFrameAsset {
  frameUrl?: string;
  /** @deprecated 使用 scale */
  padding?: number;
  /** 挂件容器相对头像尺寸的放大倍率，B 站风格约 1.45~1.55 */
  scale?: number;
  /** 头像在挂件容器内占比，约 0.6~0.66 */
  avatarRatio?: number;
}

export interface ICommentCardAsset {
  bg?: string;
  border?: string;
  radius?: number;
  shadow?: string;
  /** 装饰卡片上的文字配色，未设置时根据 bg 亮度自动推断 */
  textTheme?: 'light' | 'dark';
}

export interface IProfileBgAsset {
  bgImage?: string;
  /** 兼容旧版：整层同色遮罩 */
  overlay?: string;
  /** 渐变遮罩，如 `to bottom, rgba(0,0,0,0.2), rgba(0,0,0,0.8)` */
  overlayGradient?: string;
  /** 背景图裁切焦点，如 `center 32%` */
  objectPosition?: string;
  /** 装饰背景上的文字配色 */
  textTheme?: 'light' | 'dark';
}

export interface ICommentFontAsset {
  color?: string;
  fontFamily?: string;
  fontWeight?: number | string;
}

export interface IPostCardAsset {
  border?: string;
  borderRadius?: number;
  background?: string;
}
