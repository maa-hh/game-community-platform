import hyRequest from './request';
import type {
  IDataType,
  IChangePasswordParams,
  IPrepareChangeEmailParams,
  IConfirmChangeEmailParams,
  ICancelAccountParams,
  ISendCodeResult,
  IPageResult,
  IUserSearchItem,
} from './types';
import type { IUserCard } from './types';

/** 修改密码（成功后服务端作废会话，前端需重新登录） */
export function changePasswordApi(data: IChangePasswordParams) {
  return hyRequest.put<IDataType<null>>({
    url: '/user/password',
    data,
  });
}

/** 改邮箱：向当前绑定邮箱发验证码 */
export function sendChangeEmailOldCodeApi() {
  return hyRequest.post<IDataType<ISendCodeResult>>({
    url: '/user/email/send-old-code',
    data: {},
  });
}

/** 改邮箱：校验原邮箱验证码后，向新邮箱发码 */
export function prepareChangeEmailApi(data: IPrepareChangeEmailParams) {
  return hyRequest.post<IDataType<ISendCodeResult>>({
    url: '/user/email/prepare',
    data,
  });
}

/** 改邮箱：双码确认更换（成功后清登录态） */
export function confirmChangeEmailApi(data: IConfirmChangeEmailParams) {
  return hyRequest.put<IDataType<{ email: string }>>({
    url: '/user/email',
    data,
  });
}

/** 注销：向当前绑定邮箱发验证码（需登录） */
export function sendCancelAccountCodeApi() {
  return hyRequest.post<IDataType<ISendCodeResult>>({
    url: '/user/cancel/send-code',
    data: {},
  });
}

/** 申请注销账号（进入冷静期） */
export function cancelAccountApi(data: ICancelAccountParams) {
  return hyRequest.post<IDataType<null>>({
    url: '/user/cancel',
    data,
  });
}

/** 搜索用户（账号精确 / 昵称前缀，单页最多 20） */
export function searchUsersApi(params: {
  keyword: string;
  page?: number;
  size?: number;
}) {
  return hyRequest
    .get<IPageResult<IUserSearchItem>>({
      url: '/user/simple/search',
      params: {
        keyword: params.keyword,
        page: params.page ?? 1,
        size: Math.min(params.size ?? 10, 20),
      },
    })
    .then((res) => ({
      ...res,
      data: res.data || [],
    }));
}

/** 批量用户名片（公开，ids 为 accountId） */
export function getUsersByAccountIdsApi(accountIds: number[]) {
  const qs = accountIds.map((id) => `ids=${id}`).join('&');
  return hyRequest
    .get<IDataType<IUserCard[]>>({
      url: `/user/ids?${qs}`,
    })
    .then((res) => ({
      ...res,
      data: res.data || [],
    }));
}

/** 按 accountId 查询单个公开用户名片，个人主页使用该接口避免搜索结果歧义。 */
export function getUserSimpleByAccountIdApi(accountId: number) {
  return hyRequest
    .get<IDataType<IUserCard>>({
      url: `/user/simple/${accountId}`,
    })
    .then((res) => res);
}
