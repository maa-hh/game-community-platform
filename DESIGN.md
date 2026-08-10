# 游戏社区 · 前端设计规范（DESIGN）

> **强制**：写/改前端 UI 前先读本文，并按本文落地。目标是全站视觉、布局、交互一致。
> 变量源码：`src/assets/css/common.less` · 品牌常量：`src/constants/brand.ts`

---

## 1. 产品气质

- **定位**：游戏社区（偏「小黑盒」式信息站），不是 SaaS 仪表盘。
- **主色**：橙色 `#ff6600`（强调、激活、CTA），不要改成紫系/靛蓝渐变主题。
- **默认主题**：浅色为主；深色模式可用，登录落地页可临时强制深色。
- **避免**：默认 Inter/系统堆、大面积发光、全圆角 pill 堆砌、紫白渐变、奶油纸质报纸风。

---

## 2. 布局与页面壳

### 2.1 布局分工

| 布局 | 路径 | 用途 |
|------|------|------|
| `RootLayout` | `layouts/Root` | 挂全局 `AuthModal`（必须在 Router 内） |
| `MainLayout` | `layouts/Main` | 站内页：顶栏 + **统一内容盒** |
| `LoginLayout` | `layouts/Login` | 登录落地：顶栏 + **无限宽盒**，方便全屏视频 |
| `AuthLayout` | `layouts/Auth` | 旧分栏认证壳（如仍用） |

### 2.2 站内内容盒（核心）

- 导航栏以下内容统一由 `main-layout__container` 限制：
  - `--page-content-max-width: 960px`
  - `--page-content-gutter: 24px`
  - 水平居中
- **顶栏内容区**与内容盒同宽对齐（同一套 CSS 变量）。
- **禁止**：站内各页面再各自写一套 `max-width: 960/1080`；宽度只在布局层控制。
- **页面背景**：`MainLayout` / 内容区空背景用 `--color-bg-secondary`，与盒内无内容区一致，避免两侧突兀白边。

### 2.3 登录落地页例外

- 路由：`/login` → `LoginLayout` → `Login`
- **保留顶栏**，**不套** `main-layout__container`
- 背景视频全幅铺开（`object-fit: cover`），内容叠在视频上
- `min-height: calc(100vh - 64px)`（扣除顶栏）

---

## 3. 色彩 Token

浅色（默认）：

| Token | 值 | 用途 |
|-------|-----|------|
| `--color-primary` | `#ff6600` | 主色、激活、CTA |
| `--color-text` | `#14191e` | 主文案 |
| `--color-text-secondary` | `#8c9196` | 次要文案、铅笔旁说明 |
| `--color-bg` | `#ffffff` | 卡片/资料白底 |
| `--color-bg-secondary` | `#f3f4f5` | 页面底、空区、搜索框底 |
| `--color-border` | `#e8e8e8` | 分割线 |

深色：见 `common.less` 的 `[data-theme='dark']`，主色仍为橙。

**写样式优先 `var(--*)`，禁止魔法色散落。**

---

## 4. 顶栏（AppHeader）

- Sticky，`z-index: 100`，毛玻璃：`backdrop-filter: blur(12px)`
- 结构：品牌 · 导航链接 · **胶囊搜索** · 主题切换 · **发布内容按钮** · 头像/登录
- **发布内容**：主色按钮，放在头像/登录左侧并留间距；未登录时点开登录弹窗
- **搜索框**：
  - 胶囊形（`border-radius: 999px`）
  - 浅灰底（`--header-search-bg`）
  - 放大镜在**右侧**，可点击；回车同样触发搜索
- **头像菜单**：
  - 仅 `trigger={['click']}`，不要 hover 出菜单
  - 悬停头像：半透明蒙层
  - 箭头：关↓ / 开↑（`DownOutlined` / `UpOutlined`）

---

## 5. 卡片与列表

### 5.1 帖子类内容：统一用 ContentCard（强制）

凡是**列表/信息流里的帖子卡片**（首页 Feed、个人页帖子、浏览历史、赞过、收藏、搜索「帖子」、推荐流等），**必须**用 `base-ui/ContentCard` 展示，**禁止**在页面里手搓另一套标题/正文/图/标签/赞评布局。

| 约定 | 说明 |
|------|------|
| 组件 | `src/base-ui/ContentCard/` |
| 视觉 | 白底、浅边框、`border-radius: 12px`，轻 hover 阴影 |
| 结构 | 作者 → 标题 → 正文（最多三行省略）→ 媒体区 → 分区标签 + 评论/赞 |
| 类型 | `postType`: `image_text`（**封面图 + 纯文字正文**，小红书式）/ `article`（正文可插图）/ `video`（封面 + 播放）/ `repost`（转发） |
| 多图 | 列表：单行限高 + 等比；放不下末张标「共 N 张」。详情图文：封面横滑在上，正文在下且无插图 |
| 视频点播 | 列表只展示封面+播放标识；点击进详情再播；详情为 **16:9** 正常流播放（作者头像昵称在视频上方），不做 sticky 小窗 |

调用方按类型填好 `images` / `coverUrl` / `postType`，组件只负责统一展示。详细 props 与示例见 `src/base-ui/ContentCard/README.md`。

### 5.2 宽度对齐

- **Card 与通栏块宽度**：同一内容列内，通栏白底区与下方卡片**外缘同宽**；不要给列表区单独多套一层左右 padding 导致「上宽下窄」。
- **非 Card 通栏块**（如个人主页资料区）：白底可通栏，**内部内容**用约 `20px` 左右内边距，避免贴边。

---

## 6. 个人主页交互

### 6.1 展示信息

- 展示：**昵称 + accountId（注册生成 ID）**，下一行个性签名。
- **不要**在主资料区展示邮箱。

### 6.2 可编辑命中区

| 区域 | 行为 |
|------|------|
| 头像 | 悬停蒙层 +「点击修改头像」（可带相机图标） |
| 昵称文字 | 仅文字区域蒙层 +「点击修改昵称」；旁有铅笔+「修改昵称」文案 |
| 签名文字 | 仅文字区域蒙层 +「点击修改个性签名」；旁有铅笔+「修改签名」 |
| 铅笔按钮 | **不要**盖蒙层 |

### 6.3 蒙层风格（水印感）

- 底：很淡半透明（约 `rgba(255,255,255,0.18)` 浅色 / 深色对应反相）
- 提示字：对比度足够可读（约 `0.78~0.82` 不透明 + 浅色描边/光晕）
- **禁止**：厚重黑蒙层遮住底下字看不清

### 6.4 编辑表单

- 回车提交；签名 `Shift+Enter` 换行
- 字数：**空格/换行不计有效字符**（前后端一致）

---

## 7. 搜索

- 顶栏进入 `/search?q=...&tab=...`
- Tab：**综合 / 帖子 / 用户**（帖子可占位）
- **不要**大标题「搜索：xxx」抢视觉
- 用户搜索：纯数字 → accountId 精确；否则昵称前缀；分页触底加载；后端单页 ≤20

---

## 8. 账号安全（交互摘要）

- 改密：原密码 + 新密码 + 确认；成功清登录态
- 改邮：原邮箱码 → 新邮箱发码 → 双码确认；成功清登录态
- 注销：邮箱验证码 → 7 天冷静期；期内登录撤销
- 发码失败必须 Toast，文案可读

---

## 9. 组件与实现约束

- UI 库：**antd 5/6 项目已定版本**，写组件前查 antd Skill / CLI，不凭记忆写 props。
- 图标：`@ant-design/icons`
- 样式：优先 antd props / `ConfigProvider` token；局部用同目录 `style.less` 或 styled-components。
- **不要**引入第二套 UI 库。
- 圆角参考：`--radius-sm/md/lg`（4 / 8 / 12）；搜索胶囊例外用满圆。

---

## 10. 写前端检查清单

1. 是否读过本文与 `common.less` Token？
2. 站内页是否依赖 `MainLayout` 内容盒，而不是页面私自 `max-width`？
3. 登录页是否走 `LoginLayout`（有顶栏、无限宽）？
4. 主色是否仍为橙 `#ff6600`？
5. 空背景是否用 `--color-bg-secondary`？
6. 可编辑区蒙层是否「淡底 + 可读字」且不盖铅笔？
7. 同列通栏与卡片外缘是否齐宽？
8. 帖子列表/信息流是否统一用 `ContentCard`（未手搓另一套卡片）？
9. `npm run lint` + `npm run typecheck` 是否通过？

---

## 11. 相关文件

| 主题 | 路径 |
|------|------|
| Token | `src/assets/css/common.less` |
| 主布局 | `src/layouts/Main/` |
| 登录布局 | `src/layouts/Login/` |
| 顶栏 | `src/components/AppHeader/` |
| 内容卡 | `src/base-ui/ContentCard/`（帖子列表强制；见同目录 `README.md`） |
| 帖子详情设计 | `docs/post-detail-design.md`（完整方案在后端 `docs/post-detail-social-design.md`） |
| 视频播放器 | `src/base-ui/VideoPlayer/`（DPlayer，16:9，主题色 `#ff6600`） |
| 个人主页 | `src/views/Profile/` |
| 搜索页 | `src/views/Search/` |
| 目录规范 | `PROJECT_STRUCTURE.md` |
| Agent 入口 | `AGENTS.md` |
