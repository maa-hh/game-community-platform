import type { CropAreaPixels } from '@/utils/cropImage';

export interface IProps {
  open: boolean;
  imageSrc: string;
  onCancel: () => void;
  /** dataUrl 便于预览；blob 便于直接上传 */
  onConfirm: (dataUrl: string, blob: Blob) => void;
}

export type EasyCropperProps = {
  image?: string;
  crop: { x: number; y: number };
  zoom: number;
  aspect?: number;
  cropShape?: 'rect' | 'round';
  showGrid?: boolean;
  onCropChange: (location: { x: number; y: number }) => void;
  onZoomChange?: (zoom: number) => void;
  onCropComplete?: (
    croppedArea: CropAreaPixels,
    croppedAreaPixels: CropAreaPixels,
  ) => void;
};
