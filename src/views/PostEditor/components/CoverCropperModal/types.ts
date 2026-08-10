import type { RefObject, SyntheticEvent } from 'react';
import type { Crop, PixelCrop } from 'react-image-crop';

import type { CoverCropAspectKey } from './config';

export interface ICoverCropperModalProps {
  open: boolean;
  imageSrc: string;
  onCancel: () => void;
  onConfirm: (dataUrl: string, blob: Blob) => void;
}

export interface CoverCropperBodyProps {
  imageSrc: string;
  crop?: Crop;
  aspectKey: CoverCropAspectKey;
  aspect?: number;
  imgRef: RefObject<HTMLImageElement | null>;
  onCropChange: (pixelCrop: PixelCrop, percentCrop: Crop) => void;
  onCropComplete: (crop: PixelCrop) => void;
  onAspectKeyChange: (key: CoverCropAspectKey) => void;
  onImageLoad: (event: SyntheticEvent<HTMLImageElement>) => void;
}
