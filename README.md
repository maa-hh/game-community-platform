# 游戏社区平台前端

这是游戏社区平台的 React 前端，负责官网入口、社区信息流、游戏资料、帖子详情与编辑、个人主页、通知、商城、审核工作台等浏览器端能力。后端是同一远程仓库中的 `master` 分支；前端建议以独立的 `frontend` 分支协作，避免前后端两个独立 Git 历史互相覆盖。

## 技术栈与边界

- React 19 + TypeScript 4.9
- Create React App 5 + CRACO + LESS
- React Router 7 数据路由
- Redux Toolkit：slice 管理跨页面业务状态，RTK Query 管理服务端列表缓存
- Axios：统一由 `src/service/request.ts` 的 `HYRequest` 封装
- Ant Design 6.5.1 + `@ant-design/icons`
- DPlayer、React Quill、Masonic、图片裁剪与分片上传能力

组件不得直接调用 axios，也不得在页面中复制路由守卫、token 刷新、分页缓存或信息流卡片布局。页面通过 service、store、业务组件和 `base-ui/ContentCard` 组合完成产品功能。

## 快速开始

```bash
npm install
cp .env.example .env.development.local
npm start
```

默认开发服务器为 `http://localhost:3000`。`.env.development` 默认关闭 mock、让 CRACO devServer 把 API 转发到 `http://localhost:8080`；只想独立运行演示数据时，将 `REACT_APP_ENABLE_MOCK=true`。

需要真实后端时，先按后端仓库 `docs/architecture/README.md` 启动 gateway 和依赖服务，再启动前端。前端不读取后端数据库，也不绕过 gateway 直接请求领域服务。

## 常用命令

| 命令                       | 作用                                          |
| -------------------------- | --------------------------------------------- |
| `npm start`                | 开发模式，支持 CRACO 代理、LESS 和热更新      |
| `npm run lint`             | ESLint，禁止 warning                          |
| `npm run typecheck`        | TypeScript 类型检查，不生成文件               |
| `npm run build`            | 生产构建到被忽略的 `build/`                   |
| `npm run serve:production` | 静态托管 `build/`，并代理 API/SSE             |
| `npm test`                 | CRA 测试入口；新增测试后使用非 watch 模式执行 |
| `npm run format:check`     | 检查 `src/` 中的格式                          |

交付前至少运行：

```bash
npm run lint
npm run typecheck
npm run build
git diff --check
```

## 目录结构

```text
src/
├── assets/css/       全局 reset、token、主题和公共样式
├── base-ui/           无业务语义的基础 UI 与媒体组件
├── components/        跨页面业务组件（认证、评论、分享、通知、资料等）
├── constants/         品牌、装扮、媒体和布局常量
├── hooks/             跨页面 Hook、缓存、鉴权和实时同步
├── layouts/           Root、Home、Main 三类布局壳
├── mock/              开发环境 mock 数据和鉴权演示
├── router/            数据路由、懒加载、守卫和预加载
├── service/           HTTP、上传、SSE 与领域 API
├── store/             Redux slice 和 RTK Query 服务端缓存
├── types/             内容、帖子、游戏、通知、资料类型
├── utils/             纯函数、映射、格式化和导航工具
└── views/             路由页面及页面专属组件
```

文件归属、布局宽度、颜色 token 与组件拆分规范分别见 [`PROJECT_STRUCTURE.md`](PROJECT_STRUCTURE.md)、[`DESIGN.md`](DESIGN.md) 和 [`src/components/COMPONENT_STRUCTURE.md`](src/components/COMPONENT_STRUCTURE.md)。

## 路由总览

| 路径                | 布局 | 鉴权   | 页面                             |
| ------------------- | ---- | ------ | -------------------------------- |
| `/`                 | Home | 否     | 官网首页、全幅视频/主题入口      |
| `/community`        | Main | 否     | 最新社区帖子                     |
| `/post/:id`         | Main | 否     | 帖子详情、评论、互动、分享       |
| `/game/:appId`      | Main | 否     | 游戏详情、评价、成就和讨论       |
| `/recommend`        | Main | 否     | 热榜推荐                         |
| `/games`            | Main | 否     | 游戏发现、搜索和关注             |
| `/feed`             | Main | 是     | 登录用户关注信息流               |
| `/profile`          | Main | 是     | 当前用户资料、动态、Steam 和装扮 |
| `/search`           | Main | 是     | 用户/帖子搜索                    |
| `/shop`             | Main | 是     | 装扮商城、背包、装备             |
| `/notifications`    | Main | 是     | 通知分类、未读数和 SSE           |
| `/post/editor`      | Main | 是     | 图文/文章/视频发布和审核进度     |
| `/admin/moderation` | Main | 管理员 | 审核工单                         |

`/login` 兼容旧入口并重定向到 `/`；`/about` 兼容旧入口并重定向到 `/games`；未知路径由懒加载的 NotFound 处理。

## 与后端的协议边界

前端只把 gateway 作为外部 API 入口。领域前缀和后端文档如下：

| 前端 service                                         | Gateway 前缀                              | 后端事实来源                                                                  |
| ---------------------------------------------------- | ----------------------------------------- | ----------------------------------------------------------------------------- |
| `auth.ts`、`account.ts`、`profile.ts`、`cosmetic.ts` | `/user/**`                                | `docs/v2/user-service.md`                                                     |
| `content.ts`                                         | `/article/**`、`/category/**`、`/file/**` | `docs/v2/content-service.md`、`docs/content-posting-design.md`                |
| `social.ts`                                          | `/social/**`、`/report/**`                | `docs/v2/social-service.md`、`docs/post-detail-social-design.md`              |
| `notification.ts`                                    | `/notification/**`                        | `docs/v2/notification-service.md`                                             |
| `game.ts`、`steam.ts`、`userGame.ts`                 | `/game/**`、`/steam/**`                   | `docs/v2/steam-service-api-inventory.md`                                      |
| `hotRank.ts`                                         | `/hot-article/**`                         | `docs/v2/recommend-service-current.md`（兼容接口详见 `recommend-service.md`） |
| `search.ts`                                          | `/search/**`                              | `docs/v2/search-service.md`                                                   |
| `shop.ts`                                            | `/shop/**`                                | `docs/v2/shop-service.md`                                                     |
| `moderation.ts`                                      | `/audit/**`                               | `docs/v2/audit-service.md`                                                    |
| `danmaku.ts`                                         | `/danmaku/**`                             | 后端架构文档与 danmaku-service Controller                                     |

前后端协议发生变化时，应同一提交更新 service 类型、页面适配、mock 和本目录文档；不能只修改界面文案掩盖接口不兼容。

## 配置与部署

关键环境变量：

```env
REACT_APP_BASE_URL=                 # 生产 API 根地址；开发留空使用 CRACO 代理
REACT_APP_ENABLE_MOCK=false         # 只用于开发演示
REACT_APP_UPLOAD_CONCURRENCY=4      # 分片上传并发，自动限制为 1~8
REACT_APP_RESET_AUTH_ON_BOOT=false  # 仅测试时显式开启清理登录态
```

生产构建默认使用当前站点作为 API 根地址，部署层需要把 `/user`、`/article`、`/social`、`/notification`、`/game`、`/steam`、`/search`、`/shop`、`/audit`、`/danmaku` 等路径反代到后端 gateway。历史路由必须回退到 `build/index.html`；SSE 不得被代理层缓冲或设置短超时。

```bash
npm run build
BACKEND_URL=http://127.0.0.1:8080 npm run serve:production
```

生产环境禁止把真实密钥、账号或 `.env.*.local` 提交到仓库。`build/` 是可再生构建产物，`output/`、`tmp/` 等个人生成文件也不应加入前端提交。

## 技术文档入口

- [前端文档目录](docs/README.md)
- [前端架构、启动链路与部署](docs/frontend-architecture.md)
- [模块、service、store、hooks 和页面说明](docs/frontend-modules.md)
- [基础 UI 与业务组件技术手册](docs/frontend-components.md)
- [前后端接口、鉴权、上传和联调契约](docs/frontend-contracts.md)
- [路由说明](src/router/README.md)
- [请求层说明](src/service/README.md)
- [状态管理说明](src/store/README.md)
- [样式和 token 说明](src/assets/css/README.md)

## 协作与提交

前端与后端共享远程仓库但使用独立分支：后端保持 `master`，前端使用 `frontend`。禁止对 `master` 使用 force push。提交前确认：

1. `git status --short` 中没有不属于本次工作的文件；
2. `.env`、日志、`build/`、`node_modules/` 和个人导出文件没有被 stage；
3. lint、typecheck、build 与 diff 检查全部通过；
4. 后端变更涉及协议时，已对照对应 `docs/v2` 和 SQL/Controller；
5. 远程推送后由前后端共同完成真实服务、Cookie、CORS、SSE 和上传链路冒烟。
