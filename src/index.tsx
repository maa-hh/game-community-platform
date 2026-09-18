import React from 'react';
import ReactDOM from 'react-dom/client';
// 须在 App/store 之前执行：开发环境（或显式测试开关）一次性清除旧登录态
import './bootstrap-clear-auth';
import App from './App';

// 全局样式入口：reset + common + 基础样式
import '@/assets/css/index.less';

// 开发环境：启用 mock 虚拟数据
if (process.env.NODE_ENV === 'development') {
  require('@/mock');
}

const root = ReactDOM.createRoot(
  document.getElementById('root') as HTMLElement,
);
root.render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
