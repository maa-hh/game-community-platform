import { useMemo, useRef, useState } from 'react';
import { Button, Upload, message } from 'antd';
import {
  ArrowLeftOutlined,
  ArrowRightOutlined,
  DeleteOutlined,
  EyeOutlined,
  PlusOutlined,
  ScissorOutlined,
} from '@ant-design/icons';

import ImageLightbox from '@/base-ui/ImageLightbox';

import { moveArrayItem } from '@/utils/arrayMove';
import type { IGameTag } from '@/types/game';
import type { IGameCoverOption } from '@/utils/gameCoverOptions';
import type { EditorImage } from '@/views/PostEditor/types';
import { createImageId, revokeBlobUrl } from '@/views/PostEditor/utils';

import CoverCropperModal from '../CoverCropperModal';
import GameCoverPickerModal from '../GameCoverPickerModal';

import './style.less';

const IMAGE_MAX = 5 * 1024 * 1024;

async function resolveCropImageSrc(image: EditorImage): Promise<string> {
  if (image.file) {
    return URL.createObjectURL(image.file);
  }
  const url = image.previewUrl;
  if (url.startsWith('blob:') || url.startsWith('data:')) {
    return url;
  }
  try {
    const res = await fetch(url, { mode: 'cors' });
    if (!res.ok) throw new Error('fetch failed');
    const blob = await res.blob();
    return URL.createObjectURL(blob);
  } catch {
    return url;
  }
}

export interface ICoverImageManagerProps {
  images: EditorImage[];
  onChange: (images: EditorImage[]) => void;
  maxCount: number;
  multiple?: boolean;
  gameAppIds?: number[];
  gameOptions?: IGameTag[];
}

export default function CoverImageManager({
  images,
  onChange,
  maxCount,
  multiple = true,
  gameAppIds = [],
  gameOptions = [],
}: ICoverImageManagerProps) {
  const [gamePickerOpen, setGamePickerOpen] = useState(false);
  const [cropTarget, setCropTarget] = useState<EditorImage | null>(null);
  const [cropSrc, setCropSrc] = useState('');
  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewIndex, setPreviewIndex] = useState(0);
  const cropSrcRef = useRef<string | null>(null);

  const remainingSlots = Math.max(0, maxCount - images.length);
  const previewUrls = useMemo(
    () => images.map((img) => img.previewUrl).filter(Boolean),
    [images],
  );
  const existingUrls = useMemo(
    () =>
      images
        .map((img) => img.pendingUrl || img.previewUrl)
        .filter((url): url is string => Boolean(url)),
    [images],
  );

  const releaseCropSrc = () => {
    if (cropSrcRef.current?.startsWith('blob:')) {
      revokeBlobUrl(cropSrcRef.current);
    }
    cropSrcRef.current = null;
    setCropSrc('');
    setCropTarget(null);
  };

  const addCoverImage = (file: File) => {
    if (file.size > IMAGE_MAX) {
      message.error('封面图不能超过 5MB');
      return Upload.LIST_IGNORE;
    }
    if (images.length >= maxCount) {
      message.error(`封面最多 ${maxCount} 张`);
      return Upload.LIST_IGNORE;
    }
    onChange([
      ...images,
      {
        id: createImageId(),
        file,
        previewUrl: URL.createObjectURL(file),
        source: 'upload',
      },
    ]);
    return false;
  };

  const removeImage = (id: string) => {
    const target = images.find((img) => img.id === id);
    if (target) revokeBlobUrl(target.previewUrl);
    onChange(images.filter((img) => img.id !== id));
  };

  const moveImage = (index: number, direction: -1 | 1) => {
    const nextIndex = index + direction;
    if (nextIndex < 0 || nextIndex >= images.length) return;
    onChange(moveArrayItem(images, index, nextIndex));
  };

  const openCrop = async (image: EditorImage) => {
    try {
      const src = await resolveCropImageSrc(image);
      if (
        cropSrcRef.current?.startsWith('blob:') &&
        cropSrcRef.current !== image.previewUrl
      ) {
        revokeBlobUrl(cropSrcRef.current);
      }
      cropSrcRef.current = src.startsWith('blob:') ? src : null;
      setCropTarget(image);
      setCropSrc(src);
    } catch {
      message.error('无法加载图片，暂时无法裁剪');
    }
  };

  const applyCrop = (dataUrl: string, blob: Blob) => {
    if (!cropTarget) return;
    const file = new File([blob], `cover-${cropTarget.id}.jpg`, {
      type: 'image/jpeg',
    });
    onChange(
      images.map((img) => {
        if (img.id !== cropTarget.id) return img;
        revokeBlobUrl(img.previewUrl);
        return {
          ...img,
          file,
          pendingUrl: undefined,
          previewUrl: dataUrl,
          source: img.source ?? 'upload',
        };
      }),
    );
    releaseCropSrc();
  };

  const addGameCovers = (picked: IGameCoverOption[]) => {
    if (picked.length === 0) return;
    const next = [...images];
    picked.forEach((item) => {
      if (next.length >= maxCount) return;
      if (next.some((img) => (img.pendingUrl || img.previewUrl) === item.url)) {
        return;
      }
      next.push({
        id: createImageId(),
        pendingUrl: item.url,
        previewUrl: item.url,
        source: 'game',
        gameAppId: item.appId,
      });
    });
    onChange(next);
    setGamePickerOpen(false);
    message.success(`已添加 ${picked.length} 张游戏封面`);
  };

  const openPreview = (index: number) => {
    if (previewUrls.length === 0) return;
    setPreviewIndex(index);
    setPreviewOpen(true);
  };

  const showGamePicker = gameAppIds.length > 0;

  return (
    <div className="cover-image-manager">
      {images.length > 0 ? (
        <div className="cover-image-manager__list">
          {images.map((image, index) => (
            <div key={image.id} className="cover-image-manager__card">
              <button
                type="button"
                className="cover-image-manager__preview-hit"
                aria-label={`预览封面 ${index + 1}`}
                onClick={() => openPreview(index)}
              >
                <img src={image.previewUrl} alt={`封面 ${index + 1}`} />
              </button>
              {index === 0 ? (
                <span className="cover-image-manager__badge">主封面</span>
              ) : null}
              {image.source === 'game' ? (
                <span className="cover-image-manager__source">游戏</span>
              ) : null}
              <div className="cover-image-manager__actions">
                {multiple && index > 0 ? (
                  <button
                    type="button"
                    aria-label="左移"
                    onClick={() => moveImage(index, -1)}
                  >
                    <ArrowLeftOutlined />
                  </button>
                ) : null}
                {multiple && index < images.length - 1 ? (
                  <button
                    type="button"
                    aria-label="右移"
                    onClick={() => moveImage(index, 1)}
                  >
                    <ArrowRightOutlined />
                  </button>
                ) : null}
                <button
                  type="button"
                  aria-label="预览"
                  onClick={() => openPreview(index)}
                >
                  <EyeOutlined />
                </button>
                <button
                  type="button"
                  aria-label="裁剪"
                  onClick={() => void openCrop(image)}
                >
                  <ScissorOutlined />
                </button>
                <button
                  type="button"
                  aria-label="删除"
                  onClick={() => removeImage(image.id)}
                >
                  <DeleteOutlined />
                </button>
              </div>
            </div>
          ))}
        </div>
      ) : null}

      <div className="cover-image-manager__toolbar">
        <Upload
          accept="image/jpeg,image/png,image/webp,image/gif"
          multiple={multiple}
          showUploadList={false}
          beforeUpload={addCoverImage}
          disabled={remainingSlots <= 0}
        >
          <Button icon={<PlusOutlined />} disabled={remainingSlots <= 0}>
            上传封面
          </Button>
        </Upload>

        {showGamePicker ? (
          <Button
            disabled={remainingSlots <= 0}
            onClick={() => setGamePickerOpen(true)}
          >
            从游戏选封面
          </Button>
        ) : null}
      </div>

      <GameCoverPickerModal
        open={gamePickerOpen}
        gameAppIds={gameAppIds}
        gameOptions={gameOptions}
        remainingSlots={remainingSlots}
        existingUrls={existingUrls}
        onCancel={() => setGamePickerOpen(false)}
        onConfirm={addGameCovers}
      />

      <CoverCropperModal
        open={Boolean(cropTarget && cropSrc)}
        imageSrc={cropSrc}
        onCancel={releaseCropSrc}
        onConfirm={applyCrop}
      />

      <ImageLightbox
        open={previewOpen}
        images={previewUrls}
        current={previewIndex}
        onChange={setPreviewIndex}
        onClose={() => setPreviewOpen(false)}
      />
    </div>
  );
}
