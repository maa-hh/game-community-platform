# service 网络请求层

`src/service/` 是前端唯一的 HTTP 访问边界。页面和组件不得直接调用 axios；请求统一通过 `request.ts` 的 `HYRequest`，由服务模块按业务域导出函数。

完整的领域清单、上传/SSE/错误码和后端文档对照见 [`../../docs/frontend-contracts.md`](../../docs/frontend-contracts.md) 与 [`../../docs/frontend-modules.md`](../../docs/frontend-modules.md)。

## 当前结构

```text
service/
├── config.ts       # base URL、超时、token 与上传参数
├── request.ts      # axios 封装、鉴权刷新单飞、错误归一化
├── types.ts        # 通用响应与分页类型
├── auth.ts         # 登录、注册、密码
├── content.ts      # 帖子、文章、上传与审核
├── social.ts       # 信息流、评论、互动、关注
├── notification.ts # 通知汇总、分类消息、SSE
├── game.ts         # 游戏详情、评价、讨论
├── hotRank.ts      # 热榜
├── profile.ts      # 个人资料与装扮
├── shop.ts         # 商城与背包
├── steam.ts        # Steam 绑定与同步
└── account.ts / cosmetic.ts / danmaku.ts / moderation.ts /
    search.ts / userGame.ts
```

## 数据流约定

```text
页面/业务 hook
  ├─ 读服务端状态：RTK Query（src/store/services/）
  └─ 写操作或本地业务状态：service 函数 + Redux thunk / hook
        ↓
      HYRequest
        ↓
      后端 API（开发环境由 devServer 代理）
```

信息流已经使用 RTK Query infinite query，统一处理缓存、分页、刷新、失效和并发；后续迁移其他服务端列表时沿用同一入口，不在页面重复实现缓存 Map 和请求锁。

## 环境变量

开发环境默认 `REACT_APP_BASE_URL` 为空，由前端 devServer 代理到后端；生产环境应显式配置 API 地址或使用当前站点地址。真实后端验证时设置 `REACT_APP_ENABLE_MOCK=false`。

```env
REACT_APP_BASE_URL=
REACT_APP_ENABLE_MOCK=false
REACT_APP_UPLOAD_CONCURRENCY=4
```

修改环境变量后需要重启开发服务器。敏感信息不得写入 `.env.example` 或前端代码。

## 类型与兼容

- 新接口优先在 `service/types.ts` 使用 `IDataType<T>`、`IPageResult<T>`，不要另造同义响应类型。
- 后端可能返回字符串数字的历史字段，在 service 边界归一化；业务层使用稳定的前端类型。
- 鉴权刷新由 `request.ts` 的单飞逻辑负责；请求层只发出登录失效事件，不直接调用 Ant Design UI。
- 上传、SSE 和分页接口要保留后端返回的 `page`、`size`、`total`、游标等元数据，不能只返回数组。
- 新增接口前先对照后端 `docs/architecture/README.md` 和对应 `docs/v2/*`；修改路径、业务码或字段时同步更新类型、mock、页面和联调文档。
- service 不显示 Toast、不读 React context；错误交给调用方或统一 auth event，避免网络层依赖 UI。
