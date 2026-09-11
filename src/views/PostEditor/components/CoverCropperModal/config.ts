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
  zoomMin: 0.1,
  zoomDefault: 1,
  zoomMax: 10,
  zoomStep: 0.01,
  zoomLabel: '缩放',
  rotateText: '旋转 90°',
  hint: '可缩小或放大图片；拖动裁剪框移动，拖边角调整大小与比例，也可切换比例或旋转图片',
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
