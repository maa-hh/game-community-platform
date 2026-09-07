export const PAGE_REFRESH_EVENT = 'gc:page-refresh';

export interface PageRefreshEventDetail {
  /** 注册当前页面的异步刷新任务，供全局刷新按钮等待真实完成。 */
  track: (task: Promise<unknown>) => void;
}

export type PageRefreshEvent = CustomEvent<PageRefreshEventDetail>;
