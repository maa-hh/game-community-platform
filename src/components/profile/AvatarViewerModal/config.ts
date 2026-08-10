export const MAX_AVATAR_BYTES = 5 * 1024 * 1024;
export const ACCEPT_TYPES = ['image/jpeg', 'image/png', 'image/webp'] as const;

export const avatarViewerModalConfig = {
  title: '头像',
  width: 420,
  avatarSize: 240,
  messages: {
    auditing: '头像审核中，请稍后再改',
    invalidType: '仅支持 JPG / PNG / WEBP 图片',
    tooLarge: '头像不能超过 5MB，请压缩后再试',
    success: '头像已提交审核',
    errorPrefix: '上传头像失败',
    busyButton: '头像审核中…',
    uploadButton: '修改头像',
  },
} as const;
