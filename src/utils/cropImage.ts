/**
 * 将 react-easy-crop 裁剪区域导出为 JPEG Blob / data URL
 */
export interface CropAreaPixels {
  x: number;
  y: number;
  width: number;
  height: number;
}

function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image();
    image.addEventListener('load', () => resolve(image));
    image.addEventListener('error', (err) => reject(err));
    image.setAttribute('crossOrigin', 'anonymous');
    image.src = src;
  });
}

/** 将图片顺时针旋转 90 度，返回可继续裁剪的图片 Blob。 */
export async function rotateImage90Blob(
  imageSrc: string,
  mimeType = 'image/jpeg',
  quality = 0.9,
): Promise<Blob> {
  const image = await loadImage(imageSrc);
  const canvas = document.createElement('canvas');
  canvas.width = image.naturalHeight;
  canvas.height = image.naturalWidth;
  const ctx = canvas.getContext('2d');
  if (!ctx) throw new Error('无法创建画布');

  ctx.translate(canvas.width, 0);
  ctx.rotate(Math.PI / 2);
  ctx.drawImage(image, 0, 0);

  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => {
        if (!blob) {
          reject(new Error('旋转失败'));
          return;
        }
        resolve(blob);
      },
      mimeType,
      quality,
    );
  });
}

export async function getCroppedImageBlob(
  imageSrc: string,
  crop: CropAreaPixels,
  outputSize = 512,
  mimeType = 'image/jpeg',
  quality = 0.9,
): Promise<Blob> {
  const image = await loadImage(imageSrc);
  const canvas = document.createElement('canvas');
  canvas.width = outputSize;
  canvas.height = outputSize;
  const ctx = canvas.getContext('2d');
  if (!ctx) {
    throw new Error('无法创建画布');
  }

  ctx.drawImage(
    image,
    crop.x,
    crop.y,
    crop.width,
    crop.height,
    0,
    0,
    outputSize,
    outputSize,
  );

  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => {
        if (!blob) {
          reject(new Error('裁剪失败'));
          return;
        }
        resolve(blob);
      },
      mimeType,
      quality,
    );
  });
}

/** 按裁剪区域原比例导出（用于正文插图） */
export async function getCroppedImageBlobRect(
  imageSrc: string,
  crop: CropAreaPixels,
  maxEdge = 1920,
  mimeType = 'image/jpeg',
  quality = 0.9,
): Promise<Blob> {
  const image = await loadImage(imageSrc);
  const maxDim = Math.max(crop.width, crop.height);
  const scale = maxDim > maxEdge ? maxEdge / maxDim : 1;
  const width = Math.max(1, Math.round(crop.width * scale));
  const height = Math.max(1, Math.round(crop.height * scale));
  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  const ctx = canvas.getContext('2d');
  if (!ctx) {
    throw new Error('无法创建画布');
  }

  ctx.drawImage(
    image,
    crop.x,
    crop.y,
    crop.width,
    crop.height,
    0,
    0,
    width,
    height,
  );

  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => {
        if (!blob) {
          reject(new Error('裁剪失败'));
          return;
        }
        resolve(blob);
      },
      mimeType,
      quality,
    );
  });
}

export function blobToDataUrl(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result || ''));
    reader.onerror = () => reject(new Error('读取图片失败'));
    reader.readAsDataURL(blob);
  });
}

/** 将展示尺寸下的裁剪框换算为原图像素坐标 */
export function scalePixelCropToNatural(
  crop: CropAreaPixels,
  displayWidth: number,
  displayHeight: number,
  naturalWidth: number,
  naturalHeight: number,
): CropAreaPixels {
  if (!displayWidth || !displayHeight) {
    throw new Error('无效的图片尺寸');
  }
  const scaleX = naturalWidth / displayWidth;
  const scaleY = naturalHeight / displayHeight;
  return {
    x: Math.round(crop.x * scaleX),
    y: Math.round(crop.y * scaleY),
    width: Math.round(crop.width * scaleX),
    height: Math.round(crop.height * scaleY),
  };
}
