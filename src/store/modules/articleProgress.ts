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
  tasks: ArticleProgressTask[];
}

const initialState: ArticleProgressState = {
  tasks: [],
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
      const nextTask: ArticleProgressTask = {
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
      const existingTaskIndex = state.tasks.findIndex(
        (task) => String(task.articleId) === String(articleId),
      );
      if (existingTaskIndex >= 0) {
        state.tasks[existingTaskIndex] = nextTask;
      } else {
        state.tasks.push(nextTask);
      }
    },
    updateArticleProgress(state, action: PayloadAction<IArticleProgress>) {
      const task = state.tasks.find(
        (item) => String(item.articleId) === String(action.payload.articleId),
      );
      if (task) {
        task.progress = action.payload;
      }
    },
    clearArticleProgressTrack(
      state,
      action: PayloadAction<string | undefined>,
    ) {
      if (action.payload === undefined) {
        state.tasks = [];
        return;
      }
      state.tasks = state.tasks.filter(
        (task) => String(task.articleId) !== String(action.payload),
      );
    },
  },
});

export const {
  startArticleProgressTrack,
  updateArticleProgress,
  clearArticleProgressTrack,
} = articleProgressSlice.actions;

export default articleProgressSlice.reducer;
