# CRACO 配置说明文档

本项目使用 [CRACO](https://craco.js.org/)（Create React App Configuration Override）来覆盖 `react-scripts` 的默认配置，无需 eject 即可自定义 webpack、Jest、Babel、ESLint 等配置。

本文档记录了当前项目所做的配置、原理、使用方式以及后续可扩展的配置项。

---

## 一、当前项目做了什么

### 1. 安装依赖

```bash
npm install @craco/craco@^7.1.0 --save-dev
```

> 版本对应关系：本项目使用 `react-scripts 5.0.1`，对应 `@craco/craco 7.x`。
> 若你的 `react-scripts` 是 4.x，则应安装 `@craco/craco@^6.x`；3.x 对应 `@craco/craco@^5.x`。

### 2. 新建 `craco.config.js`（项目根目录）

```js
const path = require('path');

module.exports = {
  webpack: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
};
```

### 3. 修改 `tsconfig.json`

在 `compilerOptions` 中新增以下两行，让 TypeScript 识别 `@/*` 别名：

```json
"baseUrl": ".",
"paths": {
  "@/*": ["src/*"]
}
```

### 4. 修改 `package.json` 的 scripts

把 `react-scripts` 替换为 `craco`，CRACO 才会接管构建流程：

```json
"scripts": {
  "start": "craco start",
  "build": "craco build",
  "test": "craco test",
  "eject": "react-scripts eject"
}
```

> 注意：`eject` 仍保留 `react-scripts eject`，因为 CRACO 不提供 eject 命令。
> 一旦 eject，CRACO 的配置将失效，需要手动迁移到 webpack 配置中。

---

## 二、原理：CRACO 是怎么起作用的

### 启动流程对比

**未使用 CRACO 时：**

```
npm start  →  react-scripts start  →  使用 CRA 内置的 webpack 配置
```

**使用 CRACO 后：**

```
npm start  →  craco start  →  读取 craco.config.js  →  合并/覆盖 CRA 内置 webpack 配置  →  启动 dev server
```

CRACO 本质上是一个"配置合并器"：

1. 它接管了原本由 `react-scripts` 提供的 `start` / `build` / `test` 命令。
2. 启动时先加载 CRA 内置的默认 webpack 配置。
3. 然后读取你项目根目录的 `craco.config.js`，根据对象结构深度合并（merge）到默认配置上。
4. 最终用合并后的配置启动 webpack dev server 或执行构建。

### 别名 `@` 是怎么生效的

别名生效需要两套配置协同：

#### 1. Webpack 层（运行时打包用）

`craco.config.js` 中的 `webpack.alias` 会被注入到 webpack 的 `resolve.alias` 中。

webpack 在解析模块路径时，会把 `@` 替换为 `/Users/ma/reactproject/game-community/src`，然后再去查找文件。

例如 `import App from '@/App'` 会被 webpack 解析为 `import App from '<项目>/src/App'`。

#### 2. TypeScript 层（类型检查 + IDE 智能提示用）

`tsconfig.json` 中的 `paths` 告诉 TypeScript 编译器和 IDE：

- `@/*` 这个模式映射到 `src/*` 下的文件。
- 这样在编辑器里点击 `@/App` 能跳转，`tsc` 类型检查也能通过。

> ⚠️ 两套配置缺一不可：
>
> - 只配 webpack alias：代码能跑，但 IDE 报红、`tsc` 报错。
> - 只配 tsconfig paths：IDE 正常，但 webpack 打包时找不到模块。

---

## 三、使用示例

### 目录结构示例

```
src/
├── App.tsx
├── index.tsx
├── components/
│   ├── Header.tsx
│   └── Footer.tsx
├── utils/
│   └── request.ts
└── pages/
    └── Home.tsx
```

### 在代码中使用别名

```tsx
// ❌ 之前：相对路径，层级深时难以维护
import Header from '../../components/Header';
import request from '../../utils/request';

// ✅ 现在：使用 @ 别名，无论文件层级多深都从 src 开始
import Header from '@/components/Header';
import request from '@/utils/request';
import Home from '@/pages/Home';
import App from '@/App';
```

### 验证别名是否生效

执行以下命令确认配置加载正确：

```bash
node -e "const c = require('./craco.config.js'); console.log(c.webpack.alias)"
# 输出: { '@': '/Users/ma/reactproject/game-community/src' }
```

启动开发服务器验证：

```bash
npm start
```

如果能在代码中正常使用 `@/xxx` 且页面加载无报错，说明配置已生效。

---

## 四、还能改什么配置（扩展指南）

CRACO 支持覆盖以下几大块：`webpack`、`jest`、`babel`、`eslint`、`style`、`typescript`、`plugins`。

### 1. 更多 webpack 别名

```js
const path = require('path');

module.exports = {
  webpack: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
      '@components': path.resolve(__dirname, 'src/components'),
      '@utils': path.resolve(__dirname, 'src/utils'),
      '@assets': path.resolve(__dirname, 'src/assets'),
    },
  },
};
```

同时在 `tsconfig.json` 的 `paths` 中补充：

```json
"paths": {
  "@/*": ["src/*"],
  "@components/*": ["src/components/*"],
  "@utils/*": ["src/utils/*"],
  "@assets/*": ["src/assets/*"]
}
```

### 2. 自定义 webpack 插件（例如引入 antd 按需加载）

```js
const path = require('path');

module.exports = {
  webpack: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
    plugins: [
      // 这里可以添加 webpack 插件实例
    ],
    configure: (webpackConfig, { env, paths }) => {
      // 直接修改完整的 webpack 配置对象
      // 例如修改 devtool、splitChunks 等
      return webpackConfig;
    },
  },
};
```

### 3. 配置 Sass/Less 全局变量

```js
module.exports = {
  style: {
    postcss: {
      plugins: [require('tailwindcss'), require('autoprefixer')],
    },
  },
};
```

### 4. 配置 Babel（例如按需引入 antd）

```js
module.exports = {
  babel: {
    presets: [],
    plugins: [
      ['import', { libraryName: 'antd', libraryDirectory: 'es', style: 'css' }],
    ],
  },
};
```

### 5. 配置 Jest（别名在测试中也要生效）

```js
const path = require('path');

module.exports = {
  jest: {
    configure: {
      moduleNameMapper: {
        '^@/(.*)$': '<rootDir>/src/$1',
      },
    },
  },
};
```

### 6. 配置 ESLint

```js
module.exports = {
  eslint: {
    enable: true,
    mode: 'extends',
    configure: {
      rules: {
        'no-console': 'warn',
      },
    },
  },
};
```

### 7. 完整示例：多别名 + Jest + 代理

```js
const path = require('path');

module.exports = {
  webpack: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
      '@components': path.resolve(__dirname, 'src/components'),
      '@utils': path.resolve(__dirname, 'src/utils'),
    },
    configure: (webpackConfig) => {
      // 关闭 source map（生产环境）
      if (process.env.NODE_ENV === 'production') {
        webpackConfig.devtool = false;
      }
      return webpackConfig;
    },
  },
  jest: {
    configure: {
      moduleNameMapper: {
        '^@/(.*)$': '<rootDir>/src/$1',
        '^@components/(.*)$': '<rootDir>/src/components/$1',
      },
    },
  },
  devServer: {
    proxy: {
      '/api': {
        target: 'http://localhost:3001',
        changeOrigin: true,
      },
    },
  },
};
```

对应的 `tsconfig.json`：

```json
"baseUrl": ".",
"paths": {
  "@/*": ["src/*"],
  "@components/*": ["src/components/*"],
  "@utils/*": ["src/utils/*"]
}
```

---

## 五、常见问题

### Q1: IDE 报红"无法找到模块 @/xxx"

- 检查 `tsconfig.json` 是否配置了 `baseUrl` 和 `paths`。
- 在 Cursor/VSCode 中执行 `Cmd+Shift+P` → `Reload Window` 重新加载。
- 如果使用的是 JS（非 TS）项目，需要新建 `jsconfig.json` 而不是 `tsconfig.json`。

### Q2: 启动时报 "Module not found: Can't resolve '@/xxx'"

- 检查 `craco.config.js` 文件是否在项目根目录。
- 检查 `package.json` 的 scripts 是否已替换为 `craco start/build/test`。
- 检查 `node_modules/@craco/craco` 是否存在，必要时重新 `npm install`。

### Q3: 测试命令 `npm test` 报找不到 `@/` 模块

Jest 不走 webpack，需要在 `craco.config.js` 中单独配置 `jest.configure.moduleNameMapper`，见上方第 5 节。

### Q4: 升级 react-scripts 后 CRACO 报错

CRACO 版本必须与 react-scripts 主版本号对应：

| react-scripts | @craco/craco |
| ------------- | ------------ |
| 5.x           | 7.x          |
| 4.x           | 6.x          |
| 3.x           | 5.x          |

升级时同步更新 CRACO 版本即可。

### Q5: eject 之后还能用 CRACO 吗

不能。`npm run eject` 会把 webpack 配置暴露到项目里，CRACO 的配置将完全失效。
建议尽量不要 eject，所有自定义都通过 `craco.config.js` 完成。

---

## 六、相关文件清单

| 文件              | 作用                                         |
| ----------------- | -------------------------------------------- |
| `craco.config.js` | CRACO 主配置文件，覆盖 webpack 等配置        |
| `tsconfig.json`   | TypeScript 配置，需与 webpack alias 保持一致 |
| `package.json`    | scripts 字段使用 `craco` 命令接管构建流程    |

---

## 七、参考文档

- CRACO 官方文档：https://craco.js.org/
- CRACO 配置项全览：https://craco.js.org/configuration/
- webpack resolve.alias：https://webpack.js.org/configuration/resolve/#resolvealias
- TypeScript paths：https://www.typescriptlang.org/docs/handbook/module-resolution.html#path-mapping
