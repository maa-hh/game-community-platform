import React from 'react';
import { Link } from 'react-router-dom';

// 404 页面：匹配所有未命中的路由
function NotFound() {
  return (
    <div>
      <h1>404</h1>
      <p>页面不存在。</p>
      <Link to="/">返回首页</Link>
    </div>
  );
}

export default NotFound;
