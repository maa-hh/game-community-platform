import { request } from "./client";

export type GameAccountProfile = {
  gameAccountId?: number;
  bound: boolean;
  accountNo?: string;
  name?: string;
  level?: number;
  gold?: number;
  diamond?: number;
  currentSeasonRank?: string;
  historySeasonRank?: string;
  status?: number;
  updateTime?: string;
};

export type OwnedCharacter = {
  characterId: number;
  characterCode: string;
  name: string;
  icon?: string;
  rarity: number;
  level: number;
  status: number;
  obtainTime?: string;
};

export type OwnedSkin = {
  skinId: number;
  skinCode: string;
  characterId?: number;
  characterCode?: string;
  name: string;
  icon?: string;
  rarity: number;
  equipStatus: number;
  status: number;
  obtainTime?: string;
};

export type OwnedItem = {
  itemId: number;
  itemCode: string;
  name: string;
  icon?: string;
  itemType: number;
  rarity: number;
  quantity: number;
  status: number;
  lastObtainTime?: string;
};

export type CharacterResource = {
  characterId: number;
  characterCode: string;
  name: string;
  title?: string;
  icon?: string;
  rarity: number;
  elementType: number;
  characterType: number;
  status: number;
  owned: boolean;
};

export type SkinResource = {
  skinId: number;
  skinCode: string;
  characterId?: number;
  characterCode?: string;
  name: string;
  icon?: string;
  rarity: number;
  status: number;
  owned: boolean;
  equipStatus?: number;
};

export type ItemResource = {
  itemId: number;
  itemCode: string;
  name: string;
  itemType: number;
  rarity: number;
  icon?: string;
  maxStackCount: number;
  status: number;
  owned: boolean;
  quantity?: number;
};

export type CharacterDetail = {
  id?: string;
  characterCode: string;
  story?: string;
  background?: string;
  skills?: string[];
  tags?: string[];
  createTime?: string;
  updateTime?: string;
};

export type SkinDetail = {
  id?: string;
  skinCode: string;
  description?: string;
  theme?: string;
  previewImages?: string[];
  tags?: string[];
  createTime?: string;
  updateTime?: string;
};

export type ItemDetail = {
  id?: string;
  itemCode: string;
  description?: string;
  usageTips?: string;
  sources?: string[];
  tags?: string[];
  createTime?: string;
  updateTime?: string;
};

export type SignInReward = {
  id?: number;
  dayIndex: number;
  rewardType: number;
  businessCode?: string;
  rewardCode?: string;
  quantity: number;
  rewardName?: string;
  status?: number;
  createTime?: string;
  updateTime?: string;
};

export type SignInStatus = {
  yearMonth: string;
  signedToday: boolean;
  signCount: number;
  consecutiveDays: number;
  signBits: number;
  lastSignInDate?: string;
  signedDays: number[];
};

export type SignInResult = {
  signed: boolean;
  yearMonth: string;
  dayIndex: number;
  signCount: number;
  consecutiveDays: number;
  rewardName?: string;
  rewardType?: number;
  rewardCode?: string;
  quantity?: number;
  signTime?: string;
};

export type GameCharacterEntity = {
  id?: number;
  characterCode: string;
  name: string;
  title?: string;
  icon?: string;
  rarity?: number;
  elementType?: number;
  characterType?: number;
  status?: number;
  sortOrder?: number;
  version?: number;
};

export type GameSkinEntity = {
  id?: number;
  skinCode: string;
  characterId?: number;
  characterCode?: string;
  name: string;
  icon?: string;
  rarity?: number;
  status?: number;
  sortOrder?: number;
  version?: number;
};

export type GameItemEntity = {
  id?: number;
  itemCode: string;
  name: string;
  itemType?: number;
  rarity?: number;
  icon?: string;
  maxStackCount?: number;
  status?: number;
  sortOrder?: number;
  version?: number;
};

export type PageResult<T> = {
  data: T[];
  page: number;
  size: number;
  total: number;
};

export const gameAccountApi = {
  current: () => request<GameAccountProfile>("/game-account/me"),
  bind: (accountNo: string) => request("/game-account/bind", { method: "POST", body: { accountNo } }),
  rebind: (accountNo: string) => request("/game-account/rebind", { method: "PUT", body: { accountNo } }),
  unbind: () => request<void>("/game-account/unbind", { method: "DELETE" }),
  listOwnedCharacters: (page = 1, size = 12) =>
    request<PageResult<OwnedCharacter>>(`/game-account/assets/characters?page=${page}&size=${size}`),
  listOwnedSkins: (page = 1, size = 12) =>
    request<PageResult<OwnedSkin>>(`/game-account/assets/skins?page=${page}&size=${size}`),
  listOwnedItems: (page = 1, size = 12) =>
    request<PageResult<OwnedItem>>(`/game-account/assets/items?page=${page}&size=${size}`),
  listCharacterCatalog: (payload?: { page?: number; size?: number; keyword?: string; rarity?: number }) => {
    const params = new URLSearchParams({
      page: String(payload?.page ?? 1),
      size: String(payload?.size ?? 12)
    });
    if (payload?.keyword) {
      params.set("keyword", payload.keyword);
    }
    if (payload?.rarity !== undefined) {
      params.set("rarity", String(payload.rarity));
    }
    return request<PageResult<CharacterResource>>(`/game-account/resources/characters?${params.toString()}`);
  },
  listSkinCatalog: (payload?: { page?: number; size?: number; keyword?: string; characterId?: number; rarity?: number }) => {
    const params = new URLSearchParams({
      page: String(payload?.page ?? 1),
      size: String(payload?.size ?? 12)
    });
    if (payload?.keyword) {
      params.set("keyword", payload.keyword);
    }
    if (payload?.characterId !== undefined) {
      params.set("characterId", String(payload.characterId));
    }
    if (payload?.rarity !== undefined) {
      params.set("rarity", String(payload.rarity));
    }
    return request<PageResult<SkinResource>>(`/game-account/resources/skins?${params.toString()}`);
  },
  listItemCatalog: (payload?: { page?: number; size?: number; keyword?: string; itemType?: number }) => {
    const params = new URLSearchParams({
      page: String(payload?.page ?? 1),
      size: String(payload?.size ?? 12)
    });
    if (payload?.keyword) {
      params.set("keyword", payload.keyword);
    }
    if (payload?.itemType !== undefined) {
      params.set("itemType", String(payload.itemType));
    }
    return request<PageResult<ItemResource>>(`/game-account/resources/items?${params.toString()}`);
  },
  getCharacterDetail: (characterCode: string) => request<CharacterDetail>(`/game-account/resources/characters/${characterCode}/detail`),
  getSkinDetail: (skinCode: string) => request<SkinDetail>(`/game-account/resources/skins/${skinCode}/detail`),
  getItemDetail: (itemCode: string) => request<ItemDetail>(`/game-account/resources/items/${itemCode}/detail`),
  signIn: () => request<SignInResult>("/game-account/sign-in", { method: "POST" }),
  getSignInStatus: () => request<SignInStatus>("/game-account/sign-in/status"),
  listSignInRewards: () => request<SignInReward[]>("/game-account/sign-in/rewards"),
  pageCharacters: (payload?: { page?: number; size?: number; keyword?: string; rarity?: number; status?: number }) => {
    const params = new URLSearchParams({
      page: String(payload?.page ?? 1),
      size: String(payload?.size ?? 10)
    });
    if (payload?.keyword) {
      params.set("keyword", payload.keyword);
    }
    if (payload?.rarity !== undefined) {
      params.set("rarity", String(payload.rarity));
    }
    if (payload?.status !== undefined) {
      params.set("status", String(payload.status));
    }
    return request<PageResult<GameCharacterEntity>>(`/game-account/admin/characters?${params.toString()}`);
  },
  createCharacter: (payload: GameCharacterEntity) => request<void>("/game-account/admin/characters", { method: "POST", body: payload }),
  updateCharacter: (payload: GameCharacterEntity) => request<void>("/game-account/admin/characters", { method: "PUT", body: payload }),
  disableCharacter: (id: number) => request<void>(`/game-account/admin/characters/${id}`, { method: "DELETE" }),
  saveCharacterDetail: (payload: CharacterDetail) => request<void>("/game-account/admin/characters/detail", { method: "POST", body: payload }),
  pageSkins: (payload?: { page?: number; size?: number; keyword?: string; characterId?: number; rarity?: number; status?: number }) => {
    const params = new URLSearchParams({
      page: String(payload?.page ?? 1),
      size: String(payload?.size ?? 10)
    });
    if (payload?.keyword) {
      params.set("keyword", payload.keyword);
    }
    if (payload?.characterId !== undefined) {
      params.set("characterId", String(payload.characterId));
    }
    if (payload?.rarity !== undefined) {
      params.set("rarity", String(payload.rarity));
    }
    if (payload?.status !== undefined) {
      params.set("status", String(payload.status));
    }
    return request<PageResult<GameSkinEntity>>(`/game-account/admin/skins?${params.toString()}`);
  },
  createSkin: (payload: GameSkinEntity) => request<void>("/game-account/admin/skins", { method: "POST", body: payload }),
  updateSkin: (payload: GameSkinEntity) => request<void>("/game-account/admin/skins", { method: "PUT", body: payload }),
  disableSkin: (id: number) => request<void>(`/game-account/admin/skins/${id}`, { method: "DELETE" }),
  saveSkinDetail: (payload: SkinDetail) => request<void>("/game-account/admin/skins/detail", { method: "POST", body: payload }),
  pageItems: (payload?: { page?: number; size?: number; keyword?: string; itemType?: number; status?: number }) => {
    const params = new URLSearchParams({
      page: String(payload?.page ?? 1),
      size: String(payload?.size ?? 10)
    });
    if (payload?.keyword) {
      params.set("keyword", payload.keyword);
    }
    if (payload?.itemType !== undefined) {
      params.set("itemType", String(payload.itemType));
    }
    if (payload?.status !== undefined) {
      params.set("status", String(payload.status));
    }
    return request<PageResult<GameItemEntity>>(`/game-account/admin/items?${params.toString()}`);
  },
  createItem: (payload: GameItemEntity) => request<void>("/game-account/admin/items", { method: "POST", body: payload }),
  updateItem: (payload: GameItemEntity) => request<void>("/game-account/admin/items", { method: "PUT", body: payload }),
  disableItem: (id: number) => request<void>(`/game-account/admin/items/${id}`, { method: "DELETE" }),
  saveItemDetail: (payload: ItemDetail) => request<void>("/game-account/admin/items/detail", { method: "POST", body: payload }),
  adminListSignInRewards: () => request<SignInReward[]>("/game-account/admin/sign-in-rewards"),
  createSignInReward: (payload: SignInReward) => request<void>("/game-account/admin/sign-in-rewards", { method: "POST", body: payload }),
  updateSignInReward: (payload: SignInReward) => request<void>("/game-account/admin/sign-in-rewards", { method: "PUT", body: payload }),
  deleteSignInReward: (id: number) => request<void>(`/game-account/admin/sign-in-rewards/${id}`, { method: "DELETE" })
};
