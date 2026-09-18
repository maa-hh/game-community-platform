# store 状态管理

项目使用 Redux Toolkit。状态按职责分为三类：

| 类型 | 位置 | 规则 |
| --- | --- | --- |
| 身份与跨页面业务状态 | `src/store/modules/` | slice/thunk 管理登录、通知、上传进度、实时失效等状态 |
| 服务端状态 | `src/store/services/` | RTK Query 统一缓存、刷新、失效、分页和并发 |
| 页面临时状态 | 页面或业务 hook | 仅保留表单、弹窗、当前 tab 等局部状态 |

## 当前入口

`src/store/index.ts` 注册业务 reducers、`serverApi.reducer` 和 RTK Query middleware，并调用 `setupListeners` 支持窗口聚焦与网络恢复刷新。

组件统一使用 `useAppSelector` 和 `useAppDispatch`，根状态从 store 自动推导。

## RTK Query 约定

- endpoint 放在 `src/store/services/`，请求函数仍复用 `src/service/`。
- `queryArg` 必须包含身份或筛选维度，确保不同账号、分类和筛选条件缓存隔离。
- 列表分页使用 infinite query；合并展示前通过 `flattenFeedPages` 或同等纯函数按业务主键去重。
- 写操作成功后使用 tag invalidation 或 `updateQueryData`，不要同时维护一份独立的页面缓存副本。
- 需要保留旧数据等待新数据时使用缓存更新，不要先清空列表造成闪烁。

## Redux slice 约定

Redux 只保存跨页面业务状态和实时事件产生的失效标记。异步 thunk 必须：

1. 在 service 边界完成类型归一化；
2. 对相同分类、账号或资源增加并发门禁；
3. fulfilled 时按主键合并，避免分页重复；
4. 用请求版本或 realtime revision 防止旧请求覆盖更新后的状态。

新增模块前先确认它不是服务端缓存；如果只是接口数据、分页或刷新状态，应优先放到 RTK Query。
