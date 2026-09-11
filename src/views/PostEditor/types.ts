export type EditorImageSource = 'upload' | 'game' | 'video';

export type EditorImage = {
  id: string;
  pendingUrl?: string;
  previewUrl: string;
  file?: File;
  source?: EditorImageSource;
  gameAppId?: number;
};
