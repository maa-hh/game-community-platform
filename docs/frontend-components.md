# 基础 UI 与业务组件技术手册

## 1. 组件分层

```text
views 页面
  ├─ 页面专属 parts/components
  ├─ components/ 业务复用组件
  └─ base-ui/     无业务语义的基础 UI
```

判断标准：去掉“帖子、关注、商城、审核”等产品词后仍然能复用的是 `base-ui`；需要读 store、调 service、理解产品状态的是 `components`。同一组件必须只有一个视觉实现，列表帖子统一使用 `ContentCard`。

通用组件采用 `index.tsx` 编排；复杂组件再拆 `types.ts`、`config.ts`、`useXxx.ts` 和 `parts/`。组件输入输出优先通过显式 props 和回调表达，不在组件内部隐式修改父级数据。

## 2. base-ui 组件目录

| 组件                    | 责任                                               | 典型使用                                         |
| ----------------------- | -------------------------------------------------- | ------------------------------------------------ |
| `ClampText`             | 多行截断、展开全文                                 | 摘要、帖子正文预览                               |
| `ContentCard`           | 统一帖子列表卡：作者、标题、正文、媒体、标签、互动 | Community/Feed/Recommend/Profile/Search/游戏讨论 |
| `CoverGallery`          | 多图横向展示/查看                                  | 帖子详情、游戏资料                               |
| `CoverPoster`           | 通用封面海报                                       | 游戏卡片                                         |
| `DecoratedAvatar`       | 头像与头像框叠加                                   | 个人页、商城预览                                 |
| `FeedCardCover`         | Feed 卡片媒体封面                                  | 信息流卡片内部                                   |
| `FeedMasonryCard`       | 瀑布流信息卡                                       | 游戏/媒体探索场景                                |
| `GameCardPriceOverlay`  | 游戏卡片价格/折扣浮层                              | Games 页面                                       |
| `GamePriceTag`          | 统一价格显示                                       | 游戏详情和游戏卡                                 |
| `HotRankRowCard`        | 热榜行式卡片                                       | Recommend 页面                                   |
| `ImageLightbox`         | 图片灯箱预览                                       | 帖子图片、详情媒体                               |
| `ImageZoomControl`      | 裁剪缩放滑块/数字输入                              | 头像和封面裁剪                                   |
| `LazyImage`             | 图片加载态、失败态和懒加载                         | Steam HTML、媒体                                 |
| `ListEndHint`           | 列表加载/到底/重试提示                             | 无限列表                                         |
| `MasonryGrid`           | Masonic 瀑布流布局适配                             | Games                                            |
| `MediaCover`            | 图片/视频封面和播放标识                            | ContentCard 媒体区                               |
| `OverflowTagRow`        | 标签水平溢出处理                                   | 游戏卡片                                         |
| `PageLoading`           | 页面级 Spin                                        | 初始数据加载                                     |
| `PageSubTopBar`         | 站内二级标题/返回/刷新                             | Profile、Notifications、Editor                   |
| `PostCoverThumb`        | 帖子封面类型选择与缩略图                           | 通知、列表                                       |
| `PostRowActionBar`      | 行式帖子互动操作                                   | Profile activity                                 |
| `PostRowPreview`        | 行式引用帖子预览                                   | 转发、通知                                       |
| `ProfileBgBackdrop`     | 个人主页背景展示                                   | Profile                                          |
| `ProfileHeroPreview`    | 主页装扮预览                                       | Shop/Profile                                     |
| `StatAction`            | like/favorite/share/comment/view/reply 等统计动作  | 详情、卡片、评价                                 |
| `SteamAchievementImage` | Steam 成就图懒加载/占位                            | GameDetail                                       |
| `SteamCoverImage`       | Steam 封面地址和 fallback                          | 游戏卡、详情                                     |
| `SteamRichHtml`         | Steam 富 HTML 安全清洗和媒体解析                   | 游戏介绍                                         |
| `SurfaceCard`           | 统一白底圆角边界                                   | 详情/编辑器外壳                                  |
| `TextCoverPoster`       | 无图时按标题生成海报                               | 帖子无封面                                       |
| `UserAvatar`            | 头像、首字 fallback、尺寸                          | 任意用户展示                                     |
| `UserAvatarWithFrame`   | 用户头像 + 用户装备头像框                          | Profile/Feed                                     |
| `VideoCover`            | 视频封面、播放图标和加载                           | 列表媒体                                         |
| `VideoPlayer`           | DPlayer 16:9 播放器包装                            | 详情视频、编辑预览                               |

### 2.1 ContentCard 使用约束

`ContentCard` 接收已归一化的内容模型；调用方负责把后端 raw 数据映射成 `ContentCardData`。`postType` 支持 `image_text`、`article`、`video`、`repost`。列表只展示视频封面和播放标识，详情才创建播放器。

调用方不得复制标题/正文/封面/标签/赞评布局，也不得在列表中为不同页面写另一套帖子卡。新增帖子展示字段时，优先扩展 `types.ts` 和 `ContentCard` 的统一分支。

## 3. 全局业务组件

| 组件                                         | 责任和边界                                                                      |
| -------------------------------------------- | ------------------------------------------------------------------------------- |
| `AppHeader`                                  | 品牌、主导航、胶囊搜索、主题、发布入口、头像/登录；头像菜单 click 触发          |
| `AuthModal`                                  | 登录、注册、找回密码、成功后的导航；使用 AuthModal context，不让页面复制认证 UI |
| `ArticleProgressBanner`                      | 当前用户文章审核进度轮询和状态提示                                              |
| `CommentItem`                                | 评论、回复入口、作者/时间/点赞/删除；回复分页由内部 parts 处理                  |
| `CommentSection`                             | 评论列表、发布框、分页/刷新、登录门禁；通过 hook 管理写操作和乐观状态           |
| `CommentOps` / `CommentReply` / `ReplyPopup` | 评论操作菜单、回复按钮、快捷回复弹窗                                            |
| `EmptyState`                                 | 统一空数据、说明和操作按钮                                                      |
| `FeedPanel`                                  | Feed 内容壳、加载/刷新/错误、列表和空状态；数据由 hook/RTK Query 提供           |
| `FeedbackModal`                              | 用户反馈提交；不负责通用举报流程                                                |
| `FollowButton`                               | 关注/取消关注状态和登录门禁                                                     |
| `PageTools`                                  | MainLayout 的刷新/回顶辅助工具                                                  |
| `PostActionBar`                              | 帖子详情作者下方的点赞、收藏、分享、评论等互动                                  |
| `PostBottomBar`                              | 详情页底部固定互动/评论输入；拆为 actions 和 composer                           |
| `PostDisplayTags`                            | 帖子分类、游戏和内容类型标签                                                    |
| `PostFeedList`                               | 将 LatestPostItem 映射到 ContentCard、热榜卡或空/加载态                         |
| `PostOwnerLinks`                             | 作者操作链接，如编辑、审核进度、删除                                            |
| `ProfileUserLink`                            | 带头像/昵称的用户链接；统一导航到用户资料                                       |
| `ReportModal`                                | 举报目标、分类、补充说明和提交反馈                                              |
| `RepostBlock`                                | 转发帖子中的原帖引用块                                                          |
| `ShareCard`                                  | 生成分享预览卡/复制信息；负责展示，不负责领域写操作                             |
| `ShareSheet`                                 | 帖子分享、复制链接和转发弹窗；转发形态由 `ShareRepostModal` 统一处理            |

## 4. 认证组件

| 组件                     | 责任                               |
| ------------------------ | ---------------------------------- |
| `auth/LoginForm`         | 邮箱/密码登录，错误展示和成功回调  |
| `auth/RegisterForm`      | 邮箱验证码注册、密码确认、协议校验 |
| `auth/ResetPasswordForm` | 验证码找回密码                     |
| `auth/constants`         | 认证 tab、登录来源和返回位置类型   |

认证表单通过 thunk/service 访问后端；表单只负责字段和交互，不直接写 localStorage。成功后 auth slice 和全局 refresh 状态统一更新。

## 5. 通知组件

| 组件                                          | 责任                             |
| --------------------------------------------- | -------------------------------- |
| `NotificationCategoryPanel`                   | 分类 tab、汇总未读、加载和空状态 |
| `NotificationItemCard`                        | 根据通知类型选择具体 item        |
| `notifications/parts/NotificationCommentItem` | 评论/回复通知                    |
| `NotificationFollowItem`                      | 关注通知                         |
| `NotificationLikeFavoriteItem`                | 点赞/收藏通知                    |
| `NotificationPostCover`                       | 通知关联帖子封面                 |
| `NotificationSystemItem`                      | 系统/审核/业务通知               |
| `AggregateActorGroup`                         | 相同目标的多个行为者聚合         |

通知文本和目标路由必须通过 `notificationDisplay`、`notificationRoute` 等 utils 归一化，避免每个 item 自己拼接业务码。

## 6. 个人资料组件

| 组件                   | 责任                                                       |
| ---------------------- | ---------------------------------------------------------- |
| `AccountSecurityModal` | 改密、改邮箱、注销七日冷静期；各步骤拆 panel，成功清登录态 |
| `AvatarCropper`        | 头像裁剪和上传前预览                                       |
| `AvatarViewerModal`    | 查看/上传头像、替换头像和取消                              |
| `EditSignatureModal`   | 签名编辑、有效字符计数、回车/换行规则                      |
| `EditUsernameModal`    | 昵称编辑、版本号和审核状态                                 |
| `ProfileFeed`          | 资料页动态/活动列表，按活动类型选择卡片                    |
| `ProfilePostCard`      | Profile 帖子展示和统计                                     |
| `ProfileUserListModal` | 关注/粉丝列表、分页和用户链接                              |
| `SteamSection`         | Steam 绑定、同步状态、游戏库和资料统计                     |

编辑区域只给文字区域增加淡蒙层，不覆盖铅笔按钮；空格和换行不计有效字符的规则通过 `textCount` 和后端字段校验保持一致。

## 7. 页面专属组件

### GameDetail

`GameDetailTopBar` 负责游戏标题、关注和分享；`GameIntroPanel` 展示封面、简介、Steam HTML 和成就入口；`GameAchievementList`、`GameStatsPanel` 负责成就/统计分页；`GameReviewItem` 复用 CommentItem/ReplyPopup；`GameStoreBar` 展示价格；`GameShareSheet` 复用 ShareRepostModal。

### Games

`GameCard` 是游戏发现卡片，`GameCoverPoster` 负责封面，`GameDiscoverFilters` 负责筛选，`GameMasonryGrid` 负责瀑布流，`GameSearchModal` 负责搜索和添加/关注。它们不应被当作通用帖子卡。

### PostDetail

`PostBody` 负责根据 postType 渲染文章、图文、视频、转发和正文媒体；`PostDetailTopBar` 负责返回/作者操作；`DanmakuPlayer` 封装 DPlayer + 弹幕控制/overlay，并通过 `useDanmaku` 订阅历史/实时消息。

### PostEditor

`CoverImageManager` 管理封面顺序、裁剪和视频帧；`CoverCropperModal` 做图片裁剪；`GameCoverPickerModal` 选择游戏封面；`VideoFramePickerModal` 从视频取帧。媒体上传必须走 `content.ts` 的分片流程，禁止在这些组件中自行 axios 上传。

### Shop

`CosmeticShopCard` 是当前统一商城卡片，根据装扮 code 选择主页背景、头像框、评论卡片或占位预览；`CommentCardShopPreview` 和 `ProfileBgShopPreview` 只负责预览。旧的独立 `AvatarFrameShopCard` 已删除，避免两套购买/装备 UI 漂移。

## 8. 组件质量检查

新增或重构组件前后确认：

1. 是否应放 `base-ui`、`components` 还是当前 view；
2. 是否复用了现有 ContentCard/SurfaceCard/EmptyState/Loading；
3. Props 是否能表达输入、状态和回调，是否存在隐式全局依赖；
4. antd API 是否按项目 antd 6.5.1 查询，反馈是否使用 `App.useApp()`；
5. 样式是否遵守 DESIGN token、BEM、宽度和深色主题；
6. 是否清理了旧组件清单、示例和 imports；
7. `npm run lint`、`npm run typecheck`、`npm run build` 是否通过。
