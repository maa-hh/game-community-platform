export const USERNAME_MAX = 20;
export const USERNAME_MIN = 2;

export const editUsernameModalConfig = {
  title: '修改昵称',
  okText: '提交审核',
  width: 420,
  fieldLabel: '玩家昵称',
  placeholder: '请输入昵称',
  messages: {
    auditing: '昵称审核中，请稍后再改',
    unchanged: '昵称未变更',
    success: '已提交审核',
    errorPrefix: '修改昵称失败',
  },
} as const;
