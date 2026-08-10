import { useCallback, useEffect, useRef, useState } from 'react';
import type { SyntheticEvent } from 'react';
import { message } from 'antd';
import {
  centerCrop,
  makeAspectCrop,
  type Crop,
  type PixelCrop,
} from 'react-image-crop';

import {
  blobToDataUrl,
  getCroppedImageBlobRect,
  scalePixelCropToNatural,
} from '@/utils/cropImage';

import {
  coverCropperConfig,
  resolveCoverCropAspect,
  type CoverCropAspectKey,
} from './config';

function buildInitialCrop(
  width: number,
  height: number,
  aspect?: number,
): Crop {
  if (aspect) {
    return centerCrop(
      makeAspectCrop({ unit: '%', width: 90 }, aspect, width, height),
      width,
      height,
    );
  }
  return { unit: '%', x: 5, y: 5, width: 90, height: 90 };
}

export function useCoverCropper(
  imageSrc: string,
  onConfirm: (dataUrl: string, blob: Blob) => void,
) {
  const imgRef = useRef<HTMLImageElement | null>(null);
  const [crop, setCrop] = useState<Crop>();
  const [completedCrop, setCompletedCrop] = useState<PixelCrop>();
  const [aspectKey, setAspectKey] = useState<CoverCropAspectKey>('free');
  const [confirming, setConfirming] = useState(false);
  const aspect = resolveCoverCropAspect(aspectKey);

  const applyInitialCrop = useCallback(
    (img: HTMLImageElement) => {
      const { width, height } = img;
      if (!width || !height) return;
      const nextCrop = buildInitialCrop(width, height, aspect);
      setCrop(nextCrop);
      setCompletedCrop(undefined);
    },
    [aspect],
  );

  useEffect(() => {
    setAspectKey('free');
    setCrop(undefined);
    setCompletedCrop(undefined);
  }, [imageSrc]);

  useEffect(() => {
    const img = imgRef.current;
    if (!img?.complete || !img.naturalWidth) return;
    applyInitialCrop(img);
  }, [applyInitialCrop, aspectKey]);

  const onImageLoad = useCallback(
    (event: SyntheticEvent<HTMLImageElement>) => {
      applyInitialCrop(event.currentTarget);
    },
    [applyInitialCrop],
  );

  const onCropChange = useCallback((_: PixelCrop, percentCrop: Crop) => {
    setCrop(percentCrop);
  }, []);

  const onCropComplete = useCallback((pixelCrop: PixelCrop) => {
    setCompletedCrop(pixelCrop);
  }, []);

  const handleOk = async () => {
    const img = imgRef.current;
    if (!completedCrop || !img?.naturalWidth || !img.width) return;
    setConfirming(true);
    try {
      const naturalCrop = scalePixelCropToNatural(
        completedCrop,
        img.width,
        img.height,
        img.naturalWidth,
        img.naturalHeight,
      );
      const blob = await getCroppedImageBlobRect(imageSrc, naturalCrop);
      const dataUrl = await blobToDataUrl(blob);
      onConfirm(dataUrl, blob);
    } catch {
      message.error(coverCropperConfig.errorMessage);
    } finally {
      setConfirming(false);
    }
  };

  return {
    crop,
    aspectKey,
    aspect,
    imgRef,
    confirming,
    onCropChange,
    onCropComplete,
    onAspectKeyChange: setAspectKey,
    onImageLoad,
    handleOk,
  };
}
