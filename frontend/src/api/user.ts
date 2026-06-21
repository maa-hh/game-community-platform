import { request, requestEnvelope } from "./client";

export type LoginVO = {
  accessToken: string;
  accessTokenExpireIn: number;
  userId: number;
  accountId: number;
  username: string;
  avatar?: string;
  type: number;
  gameAccount?: string;
  auditStatus?: number;
};

export type TokenRefreshVO = {
  accessToken: string;
  accessTokenExpireIn: number;
};

export type UserVO = {
  id: number;
  accountId: number;
  username: string;
  avatar?: string;
  pendingAvatarUrl?: string;
  signature?: string;
  phone?: string;
  status: number;
  type: number;
  gameAccount?: string;
  auditStatus?: number;
  version?: number;
  followCount?: number;
  fansCount?: number;
};

export type UserSimpleVO = {
  id: number;
  accountId: number;
  username: string;
  avatar?: string;
  signature?: string;
  gameAccount?: string;
};

export type PageResult<T> = {
  records: T[];
  page: number;
  size: number;
  total: number;
};

export type UpdateUserInfoDTO = {
  version: number;
  username?: string;
  signature?: string;
  phone?: string;
  gameAccount?: string;
};

export type ChangePasswordDTO = {
  oldPassword: string;
  newPassword: string;
};

export const userApi = {
  sendCode: (phone: string) => request<string>("/user/sendCode", { method: "POST", body: { phone }, auth: false }),
  registerByPhone: (payload: { username: string; password: string; phone: string; code: string; gameAccount?: string }) =>
    request<number>("/user/register/phone", { method: "POST", body: payload, auth: false }),
  loginByAccount: (payload: { accountId: number; password: string; type?: number }) =>
    request<LoginVO>("/user/login/account", { method: "POST", body: payload, auth: false }),
  refreshToken: () => request<TokenRefreshVO>("/user/token/refresh", { method: "POST", auth: false, retryOnAuthFailure: false }),
  logout: () => request<void>("/user/logout", { method: "POST" }),
  me: () => request<UserVO>("/user/me"),
  updateInfo: (payload: UpdateUserInfoDTO) => request<void>("/user/info", { method: "PUT", body: payload }),
  changePassword: (payload: ChangePasswordDTO) => request<void>("/user/password", { method: "PUT", body: payload }),
  getUser: (accountId: number) => request<UserVO>(`/user/${accountId}`),
  getSimpleUser: (accountId: number) => request<UserSimpleVO>(`/user/simple/${accountId}`),
  getUsersByIds: (ids: number[]) => {
    const params = new URLSearchParams();
    ids.forEach((id) => params.append("ids", String(id)));
    return request<UserVO[]>(`/user/ids?${params.toString()}`);
  },
  searchUsers: async (payload: { username: string; page?: number; size?: number }) => {
    const params = new URLSearchParams({
      username: payload.username,
      page: String(payload.page ?? 1),
      size: String(payload.size ?? 12)
    });
    const envelope = await requestEnvelope<UserSimpleVO[]>(`/user/simple/search?${params.toString()}`);
    return {
      records: envelope.data,
      page: (envelope as unknown as { page: number }).page,
      size: (envelope as unknown as { size: number }).size,
      total: (envelope as unknown as { total: number }).total
    };
  },
  uploadAvatar: (file: File) => {
    const form = new FormData();
    form.append("avatar", file);
    return request<string>("/user/avatar", { method: "POST", body: form });
  }
};
