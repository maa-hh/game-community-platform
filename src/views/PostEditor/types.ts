export type EditorImageSource = 'upload' | 'game';

export type EditorImage = {
  id: string;
  pendingUrl?: string;
  previewUrl: string;
  file?: File;
  source?: EditorImageSource;
  gameAppId?: number;
};
