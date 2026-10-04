import { importWithChunkReload } from '@/router/lazyImport';

/**
 * 详情页代码分片预加载入口。
 * 卡片按下时只启动下载，不等待它完成；导航仍然立即执行，详情页先使用
 * 列表预览数据渲染，接口和 chunk 到达后再补全。
 */
export const preloadPostDetail = () =>
  importWithChunkReload('post-detail', () => import('@/views/PostDetail'));

export const preloadGameDetail = () =>
  importWithChunkReload('game-detail', () => import('@/views/GameDetail'));
