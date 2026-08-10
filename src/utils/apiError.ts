import type { IDataType } from '@/service/types';

/** 提取后端返回的可读错误内容（忽略 success） */
export function extractApiMessage(error: unknown): string {
  if (error && typeof error === 'object' && 'message' in error) {
    const msg = String((error as IDataType).message || '').trim();
    if (msg && msg !== 'success') {
      return msg;
    }
  }
  if (error instanceof Error && error.message) {
    return error.message;
  }
  return '';
}

/**
 * 前端场景前缀 + 后端 message 拼接
 * 例：formatApiError('登录失败', err) → "登录失败：密码错误"
 */
export function formatApiError(
  prefix: string,
  error: unknown,
  fallbackDetail = '请稍后重试',
): string {
  const detail = extractApiMessage(error) || fallbackDetail;
  if (!detail || detail === prefix) {
    return prefix;
  }
  return `${prefix}：${detail}`;
}
