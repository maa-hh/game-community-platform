export const SIGNATURE_MAX = 50;

export const editSignatureModalConfig = {
  title: '修改个性签名',
  okText: '提交审核',
  width: 480,
  fieldLabel: '个性签名',
  fieldExtra: '回车提交，Shift+回车换行；空格与换行不计入字数',
  placeholder: '这个人很懒，还没有签名',
  messages: {
    auditing: '个性签名审核中，请稍后再改',
    unchanged: '个性签名未变更',
    success: '已提交审核',
    errorPrefix: '修改签名失败',
  },
} as const;
