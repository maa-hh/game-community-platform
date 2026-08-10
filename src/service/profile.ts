import hyRequest from './request';
import type { IDataType, IUserInfo, FieldAuditStatus } from './types';
import { normalizeUserInfo } from './types';

export type ProfileAuditField = 'avatar' | 'username' | 'signature';

export interface IProfileFieldSubmitResult {
  taskId: number;
  field: 'USERNAME' | 'SIGNATURE' | 'AVATAR';
  auditStatus: FieldAuditStatus;
  pendingValue?: string | null;
}

/** 拉取当前用户完整资料（含 pending / 审核状态） */
export async function getCurrentUserApi(): Promise<IUserInfo> {
  const res = await hyRequest.get<IDataType<IUserInfo>>({
    url: '/user/me',
  });
  return normalizeUserInfo(res.data);
}

/** 修改昵称（异步审核） */
export function updateUsernameApi(username: string, version: number) {
  return hyRequest.put<IDataType<IProfileFieldSubmitResult>>({
    url: '/user/username',
    data: { username, version },
  });
}

/** 修改个性签名（异步审核） */
export function updateSignatureApi(signature: string, version: number) {
  return hyRequest.put<IDataType<IProfileFieldSubmitResult>>({
    url: '/user/signature',
    data: { signature, version },
  });
}

/** 上传头像文件（异步审核） */
export function uploadAvatarApi(
  file: File | Blob,
  version: number,
  filename = 'avatar.jpg',
) {
  const formData = new FormData();
  formData.append(
    'avatar',
    file instanceof File
      ? file
      : new File([file], filename, { type: file.type || 'image/jpeg' }),
  );
  formData.append('version', String(version));
  return hyRequest.post<IDataType<IProfileFieldSubmitResult>>({
    url: '/user/avatar',
    data: formData,
  });
}
