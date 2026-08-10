import { useCallback, useState } from 'react';
import { message } from 'antd';

import {
  blobToDataUrl,
  getCroppedImageBlob,
  type CropAreaPixels,
} from '@/utils/cropImage';

import { avatarCropperConfig } from './config';

export function useAvatarCropper(
  imageSrc: string,
  onConfirm: (dataUrl: string, blob: Blob) => void,
) {
  const [crop, setCrop] = useState({ x: 0, y: 0 });
  const [zoom, setZoom] = useState(1);
  const [croppedAreaPixels, setCroppedAreaPixels] =
    useState<CropAreaPixels | null>(null);
  const [confirming, setConfirming] = useState(false);

  const onCropComplete = useCallback(
    (_: CropAreaPixels, pixels: CropAreaPixels) => {
      setCroppedAreaPixels(pixels);
    },
    [],
  );

  const handleOk = async () => {
    if (!croppedAreaPixels) return;
    setConfirming(true);
    try {
      const blob = await getCroppedImageBlob(imageSrc, croppedAreaPixels);
      const dataUrl = await blobToDataUrl(blob);
      onConfirm(dataUrl, blob);
    } catch {
      message.error(avatarCropperConfig.errorMessage);
    } finally {
      setConfirming(false);
    }
  };

  return {
    crop,
    setCrop,
    zoom,
    setZoom,
    confirming,
    onCropComplete,
    handleOk,
  };
}
