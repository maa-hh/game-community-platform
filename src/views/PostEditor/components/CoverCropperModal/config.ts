export const coverCropAspectOptions = [
  { label: '自由', value: 'free' },
  { label: '1:1', value: '1:1' },
  { label: '4:3', value: '4:3' },
  { label: '16:9', value: '16:9' },
  { label: '3:4', value: '3:4' },
] as const;

export type CoverCropAspectKey =
  (typeof coverCropAspectOptions)[number]['value'];

export const coverCropperConfig = {
  title: '裁剪封面',
  okText: '确认使用',
  cancelText: '取消',
  width: 720,
  hint: '拖动裁剪框移动，拖边角调整大小与比例；也可切换常用比例',
  errorMessage: '裁剪失败，请换一张图片或稍后重试',
} as const;

export function resolveCoverCropAspect(
  key: CoverCropAspectKey,
): number | undefined {
  switch (key) {
    case '1:1':
      return 1;
    case '4:3':
      return 4 / 3;
    case '16:9':
      return 16 / 9;
    case '3:4':
      return 3 / 4;
    default:
      return undefined;
  }
}
