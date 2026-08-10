import { createSlice, type PayloadAction } from '@reduxjs/toolkit';

import { ARTICLE_STATUS, type IArticleProgress } from '@/service/content';
import type { ArticleProgressKind } from '@/utils/articleProgressMessage';

export interface ArticleProgressTask {
  articleId: string;
  title: string;
  kind: ArticleProgressKind;
  progress: IArticleProgress | null;
}

interface ArticleProgressState {
  task: ArticleProgressTask | null;
}

const initialState: ArticleProgressState = {
  task: null,
};

const articleProgressSlice = createSlice({
  name: 'articleProgress',
  initialState,
  reducers: {
    startArticleProgressTrack(
      state,
      action: PayloadAction<{
        articleId: string;
        title: string;
        kind: ArticleProgressKind;
        progress?: IArticleProgress | null;
      }>,
    ) {
      const { articleId, title, kind, progress } = action.payload;
      state.task = {
        articleId,
        title,
        kind,
        progress:
          progress ??
          ({
            articleId,
            status:
              kind === 'audit' ? ARTICLE_STATUS.PENDING : ARTICLE_STATUS.DRAFT,
            auditStageText: kind === 'audit' ? '排队审核中' : undefined,
          } as IArticleProgress),
      };
    },
    updateArticleProgress(state, action: PayloadAction<IArticleProgress>) {
      if (!state.task) return;
      state.task.progress = action.payload;
    },
    clearArticleProgressTrack(state) {
      state.task = null;
    },
  },
});

export const {
  startArticleProgressTrack,
  updateArticleProgress,
  clearArticleProgressTrack,
} = articleProgressSlice.actions;

export default articleProgressSlice.reducer;
