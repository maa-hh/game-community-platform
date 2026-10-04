import React from 'react';
import { isRouteErrorResponse, useRouteError } from 'react-router-dom';

import './RouteErrorFallback.less';

function resolveErrorMessage(error: unknown): string {
  if (isRouteErrorResponse(error)) {
    return error.status === 404 ? '页面不存在或已被移动' : error.statusText;
  }
  if (
    error instanceof Error &&
    /ChunkLoadError|Loading chunk/i.test(error.message)
  ) {
    return '页面资源更新失败，请刷新后重试';
  }
  return '页面加载失败，请稍后重试';
}

/** 路由最终兜底，避免把框架堆栈直接暴露给用户。 */
export default function RouteErrorFallback() {
  const error = useRouteError();

  return (
    <main className="route-error" role="alert">
      <div className="route-error__card">
        <h1>暂时无法打开页面</h1>
        <p>{resolveErrorMessage(error)}</p>
        <button type="button" onClick={() => window.location.reload()}>
          刷新页面
        </button>
      </div>
    </main>
  );
}
