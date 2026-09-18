# `src/assets/css/` 样式目录说明

本目录集中管理项目**全局样式**。所有页面/组件共用的样式都放在这里，业务组件的局部样式随组件自己放（参见 `PROJECT_STRUCTURE.md`）。

---

## 一、文件清单与作用

| 文件            | 作用                                               | 引入时机                          | 是否可单独引入                                |
| --------------- | -------------------------------------------------- | --------------------------------- | --------------------------------------------- |
| `normalize.css` | 第三方浏览器样式归一化，修正各浏览器默认差异       | 由 `index.less` 最先引入一次      | ❌ 不要单独引入                               |
| `reset.less`    | 项目自定义重置，在 normalize 基础上进一步清零/统一 | 由 `index.less` 引入一次          | ❌ 不要单独引入                               |
| `antd reset`    | Ant Design 基础样式重置（`antd/dist/reset.css`）   | 由 `index.less` 引入一次          | ❌ 不要单独引入                               |
| `common.less`   | 全局 CSS 变量、LESS 变量、公共工具类               | 由 `index.less` 引入一次          | ✅ 业务 `.less` 可 `@import` 局部引用变量部分 |
| `index.less`    | 全局样式统一入口，聚合上述全部 + 基础补充          | 应用入口 `src/index.tsx` 引入一次 | ❌ 全项目唯一一份                             |

---

## 二、各文件详细说明

### 1. `normalize.css` + `reset.less` — 浏览器样式重置

**职责分工：**

| 层级   | 来源                       | 做什么                                                                      |
| ------ | -------------------------- | --------------------------------------------------------------------------- |
| 第一层 | `normalize.css`（npm 包）  | 修正浏览器差异，保留有用默认样式（如 `h1` 字号、`strong` 加粗）             |
| 第二层 | `reset.less`（项目自定义） | 在 normalize 基础上做项目级清零：盒模型、列表去点、链接去下划线、按钮清零等 |

**使用规范：**

- **全项目只能由 `index.less` 按顺序引入一次**：先 `normalize.css`，再 `reset.less`。
- 不要在任何业务 `.less` 里重复 `@import` 这两个文件。
- 修改 `reset.less` 时只做"重置"相关的事，**不要写业务样式、不要写颜色变量**。
- 如果某个元素需要默认值之外的表现，请在业务组件里覆盖，而不是改 reset。

**引入顺序（在 `index.less` 中）：**

```less
@import (css) 'normalize.css/normalize.css'; // 第一层：浏览器归一化
@import './reset.less'; // 第二层：项目自定义重置
```

**`reset.less` 示例：**

```less
*,
*::before,
*::after {
  box-sizing: border-box;
}
ul,
ol {
  margin: 0;
  padding: 0;
  list-style: none;
}
```

---

### 2. `common.less` — 公共变量与工具类

**职责：** 定义全局 CSS 变量（`--color-primary` 等）、LESS 编译期变量（`@primary-color` 等）、公共工具类（`.flex-center`、`.ellipsis` 等）。

**两类变量的区别：**

| 类型      | 语法                   | 何时使用                                 |
| --------- | ---------------------- | ---------------------------------------- |
| CSS 变量  | `var(--color-primary)` | 需要**运行时切换主题**时（如深色模式）   |
| LESS 变量 | `@primary-color`       | 仅编译期使用，性能更好，但不能运行时改变 |

**使用规范：**

- 业务 `.less` 如果要用全局变量，**优先 `@import (reference) '@/assets/css/common.less'`**，避免重复输出 CSS 工具类。
- 工具类（`.flex-center` 等）可以直接在 JSX 的 `className` 中使用。
- 新增全局颜色/间距/圆角，**统一在这里声明**，不要在业务文件里写魔法数字。

**示例 1 — 在业务组件里使用 CSS 变量：**

```tsx
// views/Home/index.tsx
export default function Home() {
  return <div className="home">首页</div>;
}
```

```less
// views/Home/index.less
.home {
  color: var(--color-primary);
  padding: var(--spacing-md);
  border-radius: var(--radius-md);
}
```

**示例 2 — 在业务组件里使用 LESS 变量（需 reference 引入）：**

```less
// components/UserCard/index.less
@import (reference) '@/assets/css/common.less';

.card {
  border: 1px solid @border-color;
  border-radius: @radius;
  color: @text-color;
}
```

> `@import (reference)` 表示"仅引用，不输出"，避免 `common.less` 的工具类被多次打包进最终 CSS。

**示例 3 — 直接在 JSX 使用工具类：**

```tsx
<div className="flex-between">
  <span>标题</span>
  <span>更多</span>
</div>

<p className="ellipsis-2">这是一段很长的文本会被截断为两行…</p>
```

---

### 3. `index.less` — 全局样式入口

**职责：** 把 `normalize.css` + `reset.less` + `common.less` 按顺序聚合，并补充少量真正"全局生效"的样式（如 `#root` 高度、滚动条、文字选中色）。

**使用规范：**

- **只在 `src/index.tsx` 中引入一次**：

```tsx
// src/index.tsx
import '@/assets/css/index.less';
```

- **不要**在业务组件里再 import `index.less`。
- 全局补充样式尽量少，新增全局规则前先考虑是否应该放到 `common.less` 或业务组件里。

**当前内容：**

```less
@import (css) 'normalize.css/normalize.css'; // 必须最先
@import './reset.less'; // 其次
@import './common.less'; // 再次

html,
body,
#root {
  height: 100%;
}
// 滚动条、文字选中色等全局细节...
```

---

## 三、引入顺序与依赖关系

```
src/index.tsx
   │
   └─► @/assets/css/index.less
            ├─► normalize.css   (最先：浏览器归一化，npm 包)
            ├─► reset.less      (其次：项目自定义重置)
            └─► common.less     (再次：变量 + 工具类)
```

业务组件 `.less` 可通过 `@import (reference)` 复用 `common.less` 的变量，但**不要**再次引入 `normalize.css`、`reset.less` 或 `index.less`。

---

## 四、业务组件样式组织约定

业务组件的样式**不放在本目录**，而是随组件本身放置：

```
components/UserCard/
├── index.tsx
└── index.less        # 组件局部样式，仅本组件使用
```

```tsx
// components/UserCard/index.tsx
import './index.less';

export default function UserCard() {
  return <div className="user-card">...</div>;
}
```

```less
// components/UserCard/index.less
@import (reference) '@/assets/css/common.less';

.user-card {
  border: 1px solid @border-color;
  border-radius: @radius;
}
```

---

## 五、常见问题

### Q1: 报错 `You may need an appropriate loader to handle .less files`

说明 CRACO 的 LESS 插件没生效。检查：

1. `craco.config.js` 是否在 `plugins` 中注册了 `CracoLessPlugin`。
2. `package.json` 的 `scripts` 是否使用 `craco start/build`（不是 `react-scripts`）。
3. 确认已安装 `craco-less`、`less`、`less-loader`。

### Q2: 工具类 `.flex-center` 在 JSX 里用了但没生效

- 检查 `src/index.tsx` 是否引入了 `@/assets/css/index.less`。
- 工具类定义在 `common.less` 中，只有 `index.less` 被入口引入后才会全局生效。

### Q3: 想新增一个主题 token

先确认这个值是否真的需要全局复用。品牌主色固定为橙色 `#ff6600`，不要为了单个页面新增紫色或渐变主题。需要运行时切换的值放在 light/dark 两组 CSS 变量中；只用于 LESS 编译的值才增加 LESS 变量：

```less
:root,
[data-theme='light'] {
  --color-accent: #ff6600;
}

[data-theme='dark'] {
  --color-accent: #ff6600;
}
@accent-color: #ff6600;
```

### Q4: 怎么实现深色模式

项目由 `useTheme` 写入 `[data-theme='dark']`，因此应在 `[data-theme='dark']` 选择器覆盖 CSS 变量，业务代码使用 `var(--xxx)` 自动跟随。不要只依赖系统媒体查询，否则用户手动主题和 antd token 会不一致：

```less
[data-theme='dark'] {
  --color-bg: #1f1f1f;
  --color-text: #e6e6e6;
}
```

---

## 六、相关文件

| 文件                   | 位置       | 说明                                     |
| ---------------------- | ---------- | ---------------------------------------- |
| `craco.config.js`      | 项目根目录 | 注册 `craco-less` 插件，启用 LESS 编译   |
| `src/index.tsx`        | `src/`     | 应用入口，引入 `@/assets/css/index.less` |
| `PROJECT_STRUCTURE.md` | 项目根目录 | 全项目目录规范，包含样式文件归属约定     |
