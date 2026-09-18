# 游戏社区平台

游戏社区平台是一个面向玩家的内容与社交系统：用户可以浏览和发布游戏内容，围绕游戏进行评论、回复、点赞、收藏、关注和分享，也可以绑定 Steam、查看游戏资料、参与热榜、接收实时通知并使用社区装扮。

本仓库采用“入口分支 + 后端实现分支 + 前端实现分支”的组织方式：master 只负责项目介绍和导航，完整代码分别位于 backend 与 frontend 分支。这样既能让仓库首页保持清晰，也能避免前后端两个独立工程的目录和 Git 历史互相覆盖。

## 项目简介

    浏览器前端（frontend）
            │ HTTP / Cookie / SSE / WebSocket
            ▼
    网关（backend/gateway）
            │ JWT、CORS、路由、内部鉴权
            ▼
    领域微服务
      user · content · social · notification · steam
      recommend · search · shop · audit · ai-agent · danmaku
            │
            ▼
    MySQL · Redis · Kafka · Nacos · MongoDB · MinIO · Elasticsearch

后端对外统一经过 gateway；前端不直接访问数据库或领域服务。文章元数据、社交关系和账号数据与正文、媒体、搜索、推荐、通知等能力按领域拆分，通过服务间调用和消息事件保持协作。

## 主要实现功能

### 用户与账号安全

- 邮箱登录、注册、验证码和密码找回。
- access token + HttpOnly refresh Cookie 的双令牌会话。
- 自动刷新、跨标签页登录同步、登出和登录失效处理。
- 昵称、签名、头像、邮箱、密码和账号注销冷静期管理。
- 头像框、主页背景、评论卡片等社区装扮的背包、装备和卸载。

### 内容创作与消费

- 图文、文章、视频和转发动态四类内容。
- 分类、游戏标签、封面、正文媒体和富文本内容管理。
- 图片直传与视频/大文件分片上传，支持并发、重试、续传、合并、中止和绑定。
- 草稿、提交审核、审核进度、发布、下架、编辑和删除。
- 社区最新流、关注流、推荐热榜、个人动态和游戏讨论。

### 社交互动

- 评论、回复、评论/回复点赞。
- 帖子点赞、收藏、分享、转发和浏览历史。
- 用户关注、粉丝、拉黑、社交统计和举报。
- 互动结果通过通知、Feed 失效和缓存更新同步到相关页面。

### 游戏与 Steam

- 游戏发现、筛选、搜索、详情、价格、评分和讨论。
- Steam 授权、个人资料、游戏库、成就、游玩统计和同步。
- 关注游戏、批量检查关注状态、从 Steam 导入关注列表。
- 游戏评价、评价回复、点赞和游戏分享动态。

### 推荐、搜索和实时通知

- 日榜、周榜、总榜和分类热榜，支持周期查询和实时刷新。
- 文章、游戏、用户建议词和搜索历史。
- 通知汇总、分类消息、未读数、已读操作和 SSE 实时推送。
- 资料审核、评论、点赞、关注、文章审核等事件的通知聚合。

### 商城、审核与媒体

- 社区积分、商品分页、库存、兑换和重复购买限制。
- 管理员审核工单：领取、查看、通过、驳回和审核通知。
- AI 内容审核能力，以及人工审核协作链路。
- 视频播放、弹幕历史、实时弹幕广播和播放器相关鉴权。

## 技术栈

### 后端

| 层面           | 技术                                          |
| -------------- | --------------------------------------------- |
| 语言与构建     | Java 17、Maven                                |
| 应用框架       | Spring Boot 3、Spring Cloud Alibaba           |
| 服务治理       | Nacos、Sentinel、OpenFeign                    |
| 网关与安全     | Spring Cloud Gateway、JWT、内部请求鉴权、CORS |
| 数据访问       | MyBatis-Plus、MySQL                           |
| 文档与接口     | Springdoc/OpenAPI、统一 Result/VO/DTO 协议    |
| 缓存与消息     | Redis、Kafka                                  |
| 文档/媒体/搜索 | MongoDB、MinIO、Elasticsearch                 |
| 测试与运行     | Maven Test、Docker Compose、服务脚本          |

### 前端

| 层面 | 技术                                              |
| ---- | ------------------------------------------------- |
| 应用 | React 19、TypeScript                              |
| 构建 | Create React App 5、CRACO、LESS                   |
| 路由 | React Router 7 数据路由、懒加载和详情预加载       |
| 状态 | Redux Toolkit、RTK Query infinite query           |
| UI   | Ant Design 6.5.1、@ant-design/icons               |
| 网络 | Axios、统一 HYRequest、token refresh、SSE         |
| 媒体 | DPlayer、React Quill、图片裁剪、分片上传、Masonic |
| 质量 | ESLint、Prettier、TypeScript、生产构建检查        |

## 分支与入口

| 分支     | 内容                                              | README             | 技术文档入口            |
| -------- | ------------------------------------------------- | ------------------ | ----------------------- |
| master   | 项目首页和分支导航，不放完整业务源码              | 当前页面           | 当前页面                |
| backend  | Spring Boot 微服务、SQL、Docker、脚本和后端文档   | backend/README.md  | backend/docs/README.md  |
| frontend | React 前端、页面、组件、service、store 和前端文档 | frontend/README.md | frontend/docs/README.md |

对应入口：

- [master 分支](https://github.com/maa-hh/game-community-platform/tree/master)
- [backend 分支](https://github.com/maa-hh/game-community-platform/tree/backend)
- [backend README](https://github.com/maa-hh/game-community-platform/blob/backend/README.md)
- [backend 技术文档](https://github.com/maa-hh/game-community-platform/tree/backend/docs)
- [frontend 分支](https://github.com/maa-hh/game-community-platform/tree/frontend)
- [frontend README](https://github.com/maa-hh/game-community-platform/blob/frontend/README.md)
- [frontend 技术文档](https://github.com/maa-hh/game-community-platform/tree/frontend/docs)

### 克隆入口

后端和前端建议分别克隆到不同目录：

    git clone --branch backend git@github.com:maa-hh/game-community-platform.git game-community-platform-backend
    git clone --branch frontend git@github.com:maa-hh/game-community-platform.git game-community-platform-frontend

如果已经克隆了仓库，可在同一工作区切换到目标实现分支：

    git fetch origin
    git switch backend
    # 或
    git switch frontend

前后端分支使用同一个远程仓库，但提交、构建和发布可以独立进行。不要把前端代码复制进 backend 分支，也不要对 master 或其他分支使用 force push。

## 本地启动入口

### 后端

    cd game-community-platform-backend
    cp .env.example .env
    docker compose up -d mysql redis nacos kafka mongodb elasticsearch minio
    mvn -DskipTests package

后端服务端口、启动脚本、数据库迁移顺序和配置项见 [后端架构文档](https://github.com/maa-hh/game-community-platform/blob/backend/docs/architecture/README.md)。真实 .env、密钥、日志、进程文件和 Maven 构建产物不得提交。

### 前端

    cd game-community-platform-frontend
    npm install
    cp .env.example .env.development.local
    npm start

前端默认开发服务器为 http://localhost:3000，开发时通过 CRACO 代理访问后端 gateway http://localhost:8080。前端环境变量、路由、鉴权、上传、SSE 和生产部署见 [前端架构文档](https://github.com/maa-hh/game-community-platform/blob/frontend/docs/frontend-architecture.md) 与 [前端接口契约](https://github.com/maa-hh/game-community-platform/blob/frontend/docs/frontend-contracts.md)。

## 前后端联调关系

| 前端能力               | Gateway 前缀                        | 后端实现/文档                                            |
| ---------------------- | ----------------------------------- | -------------------------------------------------------- |
| 登录、资料、账号、装扮 | /user/**                            | user-service / docs/v2/user-service.md                   |
| 内容、分类、文件上传   | /article/**、/category/**、/file/** | content-service / docs/v2/content-service.md             |
| 评论、互动、关注、举报 | /social/**、/report/**              | social-service / docs/v2/social-service.md               |
| 游戏、Steam、游戏关注  | /game/**、/steam/**                 | steam-service / docs/v2/steam-service-api-inventory.md   |
| 热榜                   | /hot-article/**                     | recommend-service / docs/v2/recommend-service-current.md |
| 搜索                   | /search/**                          | search-service / docs/v2/search-service.md               |
| 商城                   | /shop/**                            | shop-service / docs/v2/shop-service.md                   |
| 通知与 SSE             | /notification/**                    | notification-service / docs/v2/notification-service.md   |
| 人工/AI 审核           | /audit/**、/ai/**                   | audit-service、ai-agent-service                          |
| 弹幕                   | /danmaku/**                         | danmaku-service                                          |

接口路径、HTTP 方法、业务码、鉴权方式、分页字段或事件结构变化时，必须同步更新两个实现分支的代码和文档。联调不能只依赖静态构建，还需要验证 gateway 路由、Cookie/CORS、SSE、MinIO 分片上传、数据库迁移和第三方 Steam/搜索依赖。

## 质量与安全要求

后端提交前：

    mvn -DskipTests compile
    mvn test
    git diff --check

前端提交前：

    npm run lint
    npm run typecheck
    npm run build
    git diff --check

所有密钥、JWT、SMTP、Steam API Key、数据库密码和真实账号只通过本地环境变量或部署平台注入；SQL 种子和测试脚本不得包含真实用户隐私。详细规范分别见后端 [AGENTS.md](https://github.com/maa-hh/game-community-platform/blob/backend/AGENTS.md)、[service/CODING_STANDARDS.md](https://github.com/maa-hh/game-community-platform/blob/backend/service/CODING_STANDARDS.md) 和前端 [AGENTS.md](https://github.com/maa-hh/game-community-platform/blob/frontend/AGENTS.md)。
