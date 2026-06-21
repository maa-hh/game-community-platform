const MAX_AVATAR_SIZE = 2 * 1024 * 1024;
const COMPRESSED_AVATAR_SIZE = 520 * 1024;
const SUPPORTED_TYPES = ["image/jpeg", "image/png", "image/webp"];

export function validateAvatarFile(file: File) {
  if (!SUPPORTED_TYPES.includes(file.type)) {
    throw new Error("头像仅支持 JPG、PNG、WEBP 格式。");
  }
  if (file.size > MAX_AVATAR_SIZE) {
    throw new Error("原图不能超过 2MB，请换一张更轻的头像。");
  }
}

export async function compressAvatar(file: File): Promise<File> {
  validateAvatarFile(file);
  if (file.size <= COMPRESSED_AVATAR_SIZE && file.type === "image/webp") {
    return file;
  }

  const bitmap = await createImageBitmap(file);
  const canvas = document.createElement("canvas");
  const scale = Math.min(1, 640 / Math.max(bitmap.width, bitmap.height));
  canvas.width = Math.max(1, Math.round(bitmap.width * scale));
  canvas.height = Math.max(1, Math.round(bitmap.height * scale));
  const ctx = canvas.getContext("2d");
  if (!ctx) {
    throw new Error("浏览器暂不支持头像压缩。");
  }
  ctx.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
  const blob = await canvasToBlob(canvas, "image/webp", 0.82);
  return new File([blob], file.name.replace(/\.[^.]+$/, ".webp"), { type: "image/webp" });
}

function canvasToBlob(canvas: HTMLCanvasElement, type: string, quality: number) {
  return new Promise<Blob>((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (blob) {
        resolve(blob);
      } else {
        reject(new Error("头像压缩失败，请稍后再试。"));
      }
    }, type, quality);
  });
}
