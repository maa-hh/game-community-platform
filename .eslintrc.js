// ESLint 配置：https://eslint.org/docs/user-guide/configuring
// 负责代码质量检查（未使用变量、React 规则等），并把 Prettier 作为一条规则运行
module.exports = {
  // extends：继承已有规则集，后面的会覆盖前面的同规则
  extends: [
    'react-app', // CRA 内置的核心规则（React + JS 最佳实践）
    'react-app/jest', // CRA 内置的 Jest 测试相关规则
    'plugin:prettier/recommended', // = extends eslint-config-prettier + 启用 prettier 规则为 error
  ],

  // plugins：注册 ESLint 插件（仅注册，规则需在 rules 中开启）
  plugins: ['prettier'],

  rules: {
    // 把 Prettier 的格式化差异作为 ESLint 报错抛出（'error'=阻断；'warn'=仅警告；'off'=关闭）
    'prettier/prettier': 'error',

    // React 17+ JSX 不再需要 import React，关闭此规则避免误报
    'react/react-in-jsx-scope': 'off',

    // JS 中存在未使用变量时给出警告（不阻断，便于逐步清理）
    'no-unused-vars': 'warn',

    // TS 中存在未使用变量时给出警告（@typescript-eslint 插件提供的 TS 版本）
    '@typescript-eslint/no-unused-vars': 'warn',

    // 生产环境 console.* 给警告（CRA 构建时会自动移除）；开发环境允许
    'no-console': process.env.NODE_ENV === 'production' ? 'warn' : 'off',
  },

  // settings：给某些插件提供全局配置（这里让 import 解析器识别 @ 别名）
  settings: {
    'import/resolver': {
      alias: {
        // 路径别名映射，与 tsconfig.json 的 paths、craco.config.js 的 alias 保持一致
        map: [['@', './src']],
        // 解析别名时尝试的文件扩展名
        extensions: ['.ts', '.tsx', '.js', '.jsx', '.json'],
      },
    },
  },
};
