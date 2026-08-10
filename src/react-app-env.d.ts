/// <reference types="react-scripts" />

// 让 TS 识别 .less / .css 导入（副作用或 default 均可）
declare module '*.less' {
  const classes: { readonly [key: string]: string };
  export default classes;
}

declare module '*.css' {
  const classes: { readonly [key: string]: string };
  export default classes;
}

// 扩展 process.env 类型，让 IDE 对 REACT_APP_ 环境变量有自动补全和类型提示
declare namespace NodeJS {
  interface ProcessEnv {
    /** API 基础地址，对应 .env 中的 REACT_APP_BASE_URL */
    readonly REACT_APP_BASE_URL: string;
    /** 是否启用 mock，对应 .env 中的 REACT_APP_ENABLE_MOCK（'true' | 'false'） */
    readonly REACT_APP_ENABLE_MOCK: string;
    /** CRA 内置：development | production | test */
    readonly NODE_ENV: 'development' | 'production' | 'test';
  }
}
