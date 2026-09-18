# 前端模块与功能说明

本文按代码目录说明每个模块的职责、输入输出和扩展方式。组件细节另见 [`frontend-components.md`](frontend-components.md)；接口兼容细节见 [`frontend-contracts.md`](frontend-contracts.md)。

## 1. service 网络模块

所有导出 API 都通过 `src/service/request.ts` 的 `hyRequest` 调用。service 负责请求参数、响应类型、后端兼容字段归一化和必要的领域映射；不负责 React 生命周期或 UI 提示。

| 文件              | 功能范围                                                                 | 主要接口前缀/特殊处理                                                                      |
| ----------------- | ------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------ |
| `auth.ts`         | 登录、注册、验证码、重置密码、登出、refresh                              | `/user/auth/**`；公开请求 `skipAuth`                                                       |
| `account.ts`      | 改密、改邮箱、注销、用户简表、批量用户                                   | `/user/password`、`/user/email`、`/user/cancel`、`/user/simple/**`                         |
| `profile.ts`      | 当前用户、昵称、签名、头像                                               | `/user/me`、`/user/username`、`/user/signature`、`/user/avatar`；保留版本号用于乐观锁/审核 |
| `cosmetic.ts`     | 装扮展示、背包、装备/卸载/使用                                           | `/user/cosmetic/**`；与 catalog 解析资源 JSON                                              |
| `content.ts`      | 分类、文章 CRUD、发布审核、详情、审核进度、图片/视频上传                 | `/category/**`、`/article/**`、`/file/**`；包含分片上传队列、重试、续传、合并和绑定        |
| `social.ts`       | Feed、评论、回复、点赞、收藏、分享、转发、关注、拉黑、举报、个人社交列表 | `/social/**`、`/report`；统一后端历史数字字段和帖子类型，提供 mock 分支                    |
| `notification.ts` | 通知汇总、分类消息、已读、SSE 事件解析                                   | `/notification/**`；SSE 事件兼容旧包装结构                                                 |
| `game.ts`         | 游戏详情、发现、图表、评价、评价回复、游戏分享、讨论                     | `/game/**`、`/social/game-reviews/**`；把 raw game map 为页面模型                          |
| `steam.ts`        | Steam 授权、个人资料、库、成就、同步、解绑、他人 Steam 数据              | `/steam/**`；保留分页和同步游标                                                            |
| `userGame.ts`     | 关注游戏、批量检查、从 Steam 导入、用户游戏列表                          | `/steam/follows/**`；提供列表 enrichment                                                   |
| `hotRank.ts`      | 热榜查询、热榜 item 转 Feed item                                         | `/hot-article/rank`；对应后端 `recommend-service-current.md`，兼容热榜分数、行为统计字段   |
| `search.ts`       | 搜索建议、建议词触发、历史、游戏/文章搜索                                | `/search/**`；结果映射到游戏卡或 ContentCard                                               |
| `shop.ts`         | 商品分页、余额、兑换                                                     | `/shop/**`；保留库存、购买限制和幂等状态                                                   |
| `moderation.ts`   | 审核任务分页、详情、领取、处理                                           | `/audit/moderation/**`；仅审核路由使用                                                     |
| `danmaku.ts`      | 弹幕历史、单条消息、WebSocket URL                                        | `/danmaku/**`；播放器负责展示，service 负责协议地址                                        |
| `types.ts`        | `IDataType`、分页、用户和字段审核状态                                    | service 边界的共享协议类型和用户归一化                                                     |
| `config.ts`       | base URL、超时、token key、业务码、上传参数                              | 不读取 React 状态；环境变量变化需重启构建                                                  |
| `request.ts`      | Axios 二次封装、token 注入、refresh 单飞、HTTP/业务错误                  | 不能从这里调用 UI；通过事件交给上层                                                        |

### 1.1 新增接口流程

1. 先在后端 Controller、`docs/v2` 或领域设计中确认路径、方法、业务码、分页和权限。
2. 在对应 service 文件声明请求参数和响应类型；数字字符串等兼容字段在边界转换。
3. 在页面 hook 或 thunk 调用 service，不在组件 `useEffect` 里直接请求。
4. 需要列表缓存时新增 RTK Query endpoint；需要跨页面状态才新增 slice。
5. 同步 mock（若开发体验需要）、错误文案、文档和联调检查项。

## 2. store 模块

`src/store/index.ts` 组合 reducer 并挂载 `serverApi.middleware`；组件只使用 `useAppSelector`/`useAppDispatch`。

| 模块                         | 状态                                          | 关键行为                                                                                                                                                                  |
| ---------------------------- | --------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `modules/auth.ts`            | user、登录状态、请求状态、当前会话            | `loginAction`、`registerAction`、`sendCodeAction`、`resetPasswordAction`、`logoutAction`、`fetchCurrentUserAction`；成功后写 access/auth session，退出时重置 refresh 状态 |
| `modules/articleProgress.ts` | 文章发布/审核任务进度                         | 轮询结果按文章 ID 合并；成功、驳回、处理中等状态转为 banner 文案                                                                                                          |
| `modules/notification.ts`    | 汇总、分类未读、消息分页、SSE 失效            | 初始化 metadata/bootstrap；分类读取后更新未读；事件按 ID 幂等合并                                                                                                         |
| `modules/postInteraction.ts` | 当前账号下帖子 like/favorite/share 等局部状态 | scope 按 accountId 隔离；登出清理，避免用户之间串状态                                                                                                                     |
| `modules/profileRealtime.ts` | 资料/关注/审核的 dirty、revision 和失效标记   | SSE 或写操作产生 revision，Profile/Feed hook 据此刷新                                                                                                                     |
| `services/serverApi.ts`      | `communityFeed`、`followFeed` infinite query  | 参数包含身份和筛选；pageParam 负责分页；mutation 后由 hook 触发失效/刷新                                                                                                  |
| `services/feedCache.ts`      | Feed 纯函数                                   | flatten、按主键合并、局部更新；不持有 React 状态                                                                                                                          |

### 2.1 状态更新原则

- 服务器列表优先 RTK Query，不创建第二份页面缓存。
- Redux thunk 处理跨页面动作和服务端写入，不把 JSX 逻辑放进 slice。
- fulfilled 时按业务主键合并，防止重复页覆盖已有较新数据。
- 需要处理竞态时使用版本/revision；请求完成顺序不能决定最终显示的旧值。

## 3. hooks 模块

### 全局运行时 hooks

| Hook                                           | 责任                                     |
| ---------------------------------------------- | ---------------------------------------- |
| `useAuthModal`                                 | 全局登录弹窗上下文和打开/关闭认证内容    |
| `useTheme`                                     | light/dark、CSS `data-theme`、antd token |
| `useCrossTabAuthSync`                          | 监听 storage/auth event，同步登录和登出  |
| `useNotificationSse`                           | 登录后建立通知流、断线重连和事件分发     |
| `useProfileAuditPoll`                          | 有审核中的资料字段时轮询状态             |
| `usePersistentPostUploadRecovery`              | 恢复未完成的文章媒体分片会话             |
| `useDecorationRegistry` / `useUserDecorations` | 资料/评论装扮数据注册、批量读取和缓存    |

### 数据、导航和列表 hooks

| Hook                                                          | 责任                                    |
| ------------------------------------------------------------- | --------------------------------------- |
| `useInfiniteScroll`、`useCursorList`、`usePageList`           | 页码/游标列表加载和触底触发             |
| `usePageRefresh`、`useActiveRouteView`                        | 路由刷新、页面激活和缓存失效            |
| `pageDataCache`                                               | 短期页面预览/返回缓存；不替代 RTK Query |
| `useGoBack`、`useRequireLogin`                                | 安全返回和登录门禁                      |
| `useOptimisticAction`                                         | 点赞、收藏、关注等乐观更新和失败回滚    |
| `useFeedItemLike`、`useFeedItemFavorite`、`usePostLikeAction` | 复用 feed/detail 互动写操作             |
| `usePostInteraction`                                          | 详情页互动状态与缓存同步                |
| `useProfileView`、`useProfileFollowingSync`                   | 主页载入、关注后局部同步                |

### 内容/媒体 hooks

| Hook                                  | 责任                                 |
| ------------------------------------- | ------------------------------------ |
| `useArticleOwnerActions`              | 文章作者的编辑、发布、撤回、删除操作 |
| `useArticleProgressPoll`              | 文章审核进度订阅/轮询                |
| `useReportModal`                      | 举报弹窗状态与提交门禁               |
| `useTitlePosterUrl`、`useVideoPoster` | 标题海报、视频首帧/封面解析          |

## 4. views 页面模块

| 页面目录           | 页面行为                                           | 核心依赖                                   |
| ------------------ | -------------------------------------------------- | ------------------------------------------ |
| `Home`             | 官网首屏、视频背景、登录入口、主题                 | `HomeLayout`、`useAuthModal`               |
| `Community`        | 游客最新帖子、无限滚动、点赞/收藏                  | `PostFeedList`、community RTK Query        |
| `Feed`             | 登录用户关注流、刷新、失效同步                     | follow RTK Query、FeedPanel                |
| `Recommend`        | 热榜周期、日期筛选和热度卡片                       | `hotRank`、PostFeedList                    |
| `Games`            | 游戏发现、搜索建议、筛选、关注、瀑布流             | `game`、`userGame`、MasonryGrid            |
| `GameDetail`       | 游戏资料、价格、成就、Steam 统计、评价和讨论       | `useGameDetail`、多个 GameDetail parts     |
| `PostDetail`       | 帖子作者、媒体/正文、互动、评论、转发、视频/弹幕   | `PostBody`、`CommentSection`、DPlayer      |
| `PostEditor`       | 标题、富文本、封面、视频、分类、游戏关联、发布审核 | `content`、分片上传、恢复 hook             |
| `Profile`          | 资料编辑、动态、关注粉丝、Steam、装扮、安全设置    | profile components、account/cosmetic/steam |
| `Search`           | 搜索 tab、用户/帖子结果、触底分页                  | `search`、ContentCard/ProfileUserLink      |
| `Notifications`    | 分类通知、未读、加载和已读                         | notification slice、SSE                    |
| `Shop`             | 商品分类、余额、购买、背包和装备                   | shop/cosmetic、CosmeticShopCard            |
| `Admin/Moderation` | 审核列表、详情、领取、通过/驳回                    | `moderation`、AdminGuard                   |
| `NotFound`         | 未知路由的恢复入口                                 | lazy route                                 |

页面目录中的 `parts/` 和 `components/` 只属于该页面；当相同交互跨两个以上页面复用时，才提升到 `src/components/`。

## 5. base、constants、types、utils

- `base-ui/`：不读取 Redux、不调用 service，使用通用 props；详情见 `frontend-components.md`。
- `constants/`：品牌、内容类型、媒体展示、头像框、主页背景、评论卡片和布局常量；不得在 JSX 内重复写 catalog。
- `types/`：跨模块稳定类型；service 的后端 raw 类型和页面 domain 类型分离，映射在 service/utils 边界完成。
- `utils/`：纯函数为主，包括时间/计数/价格/Steam HTML/通知路由/帖子映射/导航和缓存键；不得产生 React 副作用或直接弹 Toast。

## 6. mock 模块

`src/index.tsx` 只在开发环境并且 `ENABLE_MOCK` 为 true 时加载 mock。`mock/index.ts` 覆盖认证、用户、热榜和部分游戏/文章接口；`mock/posts.ts`、`mock/profile.ts` 提供帖子和资料演示数据。

mock 的目标是本地 UI 开发，不保证模拟所有后端约束。新增/修改接口时要明确 mock 与真实 API 的差异，真实联调必须使用 `REACT_APP_ENABLE_MOCK=false`。
