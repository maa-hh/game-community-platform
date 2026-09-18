import { useCallback, useEffect, useRef, useState } from 'react';
import type { SyntheticEvent } from 'react';
import { App } from 'antd';
import {
  centerCrop,
  convertToPixelCrop,
  makeAspectCrop,
  type Crop,
  type PixelCrop,
} from 'react-image-crop';

import {
  blobToDataUrl,
  getCroppedImageBlobRect,
  rotateImage90Blob,
  scalePixelCropToNatural,
} from '@/utils/cropImage';

import {
  coverCropperConfig,
  resolveCoverCropAspect,
  type CoverCropAspectKey,
} from './config';

const INITIAL_CROP_PERCENT = 90;

function resolveCropPercent(zoom: number): number {
  if (zoom >= 1) return INITIAL_CROP_PERCENT / zoom;
  const zoomRange = 1 - coverCropperConfig.zoomMin;
  if (!zoomRange) return INITIAL_CROP_PERCENT;
  const progress = (zoom - coverCropperConfig.zoomMin) / zoomRange;
  return 100 - (100 - INITIAL_CROP_PERCENT) * progress;
}

function buildInitialCrop(
  width: number,
  height: number,
  aspect?: number,
  zoom: number = coverCropperConfig.zoomMin,
): Crop {
  const initialWidth = Math.min(100, resolveCropPercent(zoom));
  if (aspect) {
    return centerCrop(
      makeAspectCrop({ unit: '%', width: initialWidth }, aspect, width, height),
      width,
      height,
    );
  }
  return {
    unit: '%',
    x: (100 - initialWidth) / 2,
    y: (100 - initialWidth) / 2,
    width: initialWidth,
    height: initialWidth,
  };
}

function scalePercentCrop(crop: Crop, scale: number): Crop {
  if (crop.unit !== '%') return crop;
  const boundedScale = Math.min(scale, 100 / crop.width, 100 / crop.height);
  const width = crop.width * boundedScale;
  const height = crop.height * boundedScale;
  const centerX = crop.x + crop.width / 2;
  const centerY = crop.y + crop.height / 2;

  return {
    unit: '%',
    x: Math.min(100 - width, Math.max(0, centerX - width / 2)),
    y: Math.min(100 - height, Math.max(0, centerY - height / 2)),
    width,
    height,
  };
}

export function useCoverCropper(
  imageSrc: string,
  onConfirm: (dataUrl: string, blob: Blob) => void,
) {
  const { message } = App.useApp();
  const imgRef = useRef<HTMLImageElement | null>(null);
  const cropRef = useRef<Crop | undefined>(undefined);
  const [crop, setCrop] = useState<Crop>();
  const [completedCrop, setCompletedCrop] = useState<PixelCrop>();
  const [aspectKey, setAspectKey] = useState<CoverCropAspectKey>('free');
  const [confirming, setConfirming] = useState(false);
  const [rotating, setRotating] = useState(false);
  const [rotation, setRotation] = useState(0);
  const [zoom, setZoom] = useState<number>(coverCropperConfig.zoomDefault);
  const zoomRef = useRef<number>(coverCropperConfig.zoomDefault);
  const [workingImageSrc, setWorkingImageSrc] = useState(imageSrc);
  const aspect = resolveCoverCropAspect(aspectKey);

  const applyInitialCrop = useCallback(
    (img: HTMLImageElement) => {
      const { width, height } = img;
      if (!width || !height) return;
      const nextCrop = buildInitialCrop(width, height, aspect, zoomRef.current);
      cropRef.current = nextCrop;
      setCrop(nextCrop);
      setCompletedCrop(undefined);
    },
    [aspect],
  );

  useEffect(() => {
    setAspectKey('free');
    cropRef.current = undefined;
    setCrop(undefined);
    setCompletedCrop(undefined);
    setRotation(0);
    zoomRef.current = coverCropperConfig.zoomDefault;
    setZoom(coverCropperConfig.zoomDefault);
    setWorkingImageSrc(imageSrc);
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

  const onRotate = useCallback(async () => {
    if (rotating) return;
    setRotating(true);
    try {
      const blob = await rotateImage90Blob(workingImageSrc);
      const rotatedSrc = await blobToDataUrl(blob);
      setWorkingImageSrc(rotatedSrc);
      setRotation((previous) => (previous + 90) % 360);
      cropRef.current = undefined;
      setCrop(undefined);
      setCompletedCrop(undefined);
    } catch {
      message.error('图片旋转失败，请稍后重试');
    } finally {
      setRotating(false);
    }
  }, [message, rotating, workingImageSrc]);

  const onCropChange = useCallback((_: PixelCrop, percentCrop: Crop) => {
    cropRef.current = percentCrop;
    setCrop(percentCrop);
  }, []);

  const onCropComplete = useCallback((pixelCrop: PixelCrop) => {
    setCompletedCrop(pixelCrop);
  }, []);

  const onZoomChange = useCallback((nextZoom: number) => {
    const clampedZoom = Math.min(
      coverCropperConfig.zoomMax,
      Math.max(coverCropperConfig.zoomMin, nextZoom),
    );
    const previousZoom = zoomRef.current;
    if (clampedZoom === previousZoom) return;

    zoomRef.current = clampedZoom;
    setZoom(clampedZoom);
    const currentCrop = cropRef.current;
    if (!currentCrop) return;

    const nextCrop = scalePercentCrop(
      currentCrop,
      resolveCropPercent(clampedZoom) / resolveCropPercent(previousZoom),
    );
    cropRef.current = nextCrop;
    setCrop(nextCrop);
    const img = imgRef.current;
    if (img?.width && img.height) {
      setCompletedCrop(convertToPixelCrop(nextCrop, img.width, img.height));
    } else {
      setCompletedCrop(undefined);
    }
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
      const blob = await getCroppedImageBlobRect(workingImageSrc, naturalCrop);
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
    zoom,
    rotation,
    rotating,
    workingImageSrc,
    onCropChange,
    onCropComplete,
    onAspectKeyChange: setAspectKey,
    onImageLoad,
    onZoomChange,
    onRotate,
    handleOk,
  };
}
