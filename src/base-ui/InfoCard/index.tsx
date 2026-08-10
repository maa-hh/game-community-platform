import React, { memo } from 'react';
import type { FC, ReactNode } from 'react';

// Props 接口：用 IProps 命名，明确组件接收的参数类型
interface IProps {
  children?: ReactNode;
  name: string;
  age: number;
  height?: number;
}

// FC<IProps>：函数组件 + 泛型标注 props 类型，IDE 有完整提示
const InfoCard: FC<IProps> = (props) => {
  return (
    <div className="info-card">
      <div>name: {props.name}</div>
      <div>age: {props.age}</div>
      <div>height: {props.height ?? '未填写'}</div>
      <div>{props.children}</div>
    </div>
  );
};

// memo：props 不变时跳过重渲染，适合纯展示型组件
export default memo(InfoCard);
