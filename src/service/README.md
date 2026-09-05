# `service/` 网络请求层说明

本目录负责所有 HTTP 请求，组件**不直接调 axios**，统一走 `service/` 暴露的函数。

---

## 一、目录结构

```
service/
├── config.ts       # API 全局配置（baseURL、timeout、token key）
├── request.ts      # axios 二次封装（HYRequest）
├── types.ts        # API 响应类型定义
├── home.ts         # 首页相关 API
├── recommend.ts    # 推荐页相关 API
└── README.md
```

---

## 二、数据流架构

```
页面 render
    ↓ dispatch(asyncThunk)
store (createAsyncThunk)
    ↓ 调用 service 方法
service/home.ts → getBanners()
    ↓ 调用封装的 axios
service/request.ts (HYRequest)
    ↓ GET /api/banner
mock/index.ts（开发环境拦截 URL，返回虚拟数据）
    ↓
store 更新 state → 页面 useAppSelector 渲染
```

---

## 三、config.ts — 全局配置

```ts
export const BASE_URL =
  process.env.REACT_APP_BASE_URL || 'http://localhost:3000';
export const TIMEOUT = 10000;
export const ACCESS_TOKEN_KEY = 'game_community_access_token';
export const ENABLE_MOCK = ...; // 开发环境默认 true
```

| 配置项             | 作用                  | 自定义方式                                   |
| ------------------ | --------------------- | -------------------------------------------- |
| `BASE_URL`         | API 基础地址          | `.env` 中设置 `REACT_APP_BASE_URL`           |
| `TIMEOUT`          | 请求超时              | 修改 `config.ts`                             |
| `ACCESS_TOKEN_KEY` | access token 存储 key | 修改 `config.ts`                             |
| `ENABLE_MOCK`      | 是否启用 mock         | `.env` 设 `REACT_APP_ENABLE_MOCK=false` 关闭 |

`.env.development` 示例（项目根目录，已创建）：

```
REACT_APP_BASE_URL=http://localhost:3000
REACT_APP_ENABLE_MOCK=true
```

#### `process.env.REACT_APP_BASE_URL` 怎么来的？

CRA 在**启动/构建时**读取项目根目录的 `.env` 文件，把 `REACT_APP_` 开头的变量**注入**到代码里：

```
.env.development（npm start 时读取）
    ↓ webpack 编译时替换
process.env.REACT_APP_BASE_URL
    ↓ 代码里直接使用
export const BASE_URL = process.env.REACT_APP_BASE_URL || 'http://localhost:3000';
```

#### 怎么配置？

在项目根目录创建/修改 `.env` 文件：

| 文件                     | 何时加载               | 是否提交 git              |
| ------------------------ | ---------------------- | ------------------------- |
| `.env`                   | 所有环境               | ✅ 可提交（放公共默认值） |
| `.env.development`       | `npm start` 开发时     | ✅ 可提交                 |
| `.env.production`        | `npm run build` 生产时 | ✅ 可提交                 |
| `.env.local`             | 所有环境，优先级最高   | ❌ 不提交（个人本地配置） |
| `.env.development.local` | 开发环境本地覆盖       | ❌ 不提交                 |

**规则：**

1. 变量名**必须以 `REACT_APP_` 开头**，否则不会注入前端（安全机制）
2. 修改 `.env` 后必须**重启** `npm start` 才生效
3. 代码里用 `process.env.REACT_APP_XXX` 读取

**示例：**

```bash
# .env.development（开发）
REACT_APP_BASE_URL=http://localhost:3000
REACT_APP_ENABLE_MOCK=true

# .env.production（生产上线）
REACT_APP_BASE_URL=https://api.game-community.com
REACT_APP_ENABLE_MOCK=false
```

**切换真实后端：** 把 `REACT_APP_BASE_URL` 改成后端地址，设 `REACT_APP_ENABLE_MOCK=false`，重启 dev server。

#### IDE 自动补全（`src/react-app-env.d.ts`）

在 `react-app-env.d.ts` 中扩展 `ProcessEnv` 后，输入 `process.env.` 时 IDE 会提示已声明的环境变量：

```ts
declare namespace NodeJS {
  interface ProcessEnv {
    readonly REACT_APP_BASE_URL: string;
    readonly REACT_APP_ENABLE_MOCK: string;
    readonly NODE_ENV: 'development' | 'production' | 'test';
  }
}
```

新增环境变量时，同步在 `.env.example` 和 `react-app-env.d.ts` 各加一项。

---

## 四、request.ts — HYRequest 封装

### 提供的方法

| 方法              | 作用                       |
| ----------------- | -------------------------- |
| `request(config)` | 底层请求，其他方法都走这里 |
| `get(config)`     | GET 请求                   |
| `post(config)`    | POST 请求                  |
| `put(config)`     | PUT 请求                   |
| `delete(config)`  | DELETE 请求                |

### 拦截器详解

#### 接口定义 `IHYInterceptors`

```ts
export interface IHYInterceptors {
  requestInterceptor?: (
    config: InternalAxiosRequestConfig,
  ) => InternalAxiosRequestConfig;
  requestInterceptorCatch?: (error: unknown) => unknown;
  responseInterceptor?: (
    res: AxiosResponse,
  ) => AxiosResponse | Promise<AxiosResponse>;
  responseInterceptorCatch?: (error: unknown) => unknown;
}
```

#### `requestInterceptor` 类型拆解

```ts
requestInterceptor?: (
  config: InternalAxiosRequestConfig,
) => InternalAxiosRequestConfig;
```

| 部分                                 | 含义                                    |
| ------------------------------------ | --------------------------------------- |
| `requestInterceptor`                 | 字段名：请求拦截器                      |
| `?`                                  | 可选，可以不传                          |
| `config: InternalAxiosRequestConfig` | 参数：axios 的请求配置对象              |
| `=> InternalAxiosRequestConfig`      | 返回值：处理后的配置（**必须 return**） |

**作用：** 每次发请求之前，axios 先把 `config` 交给这个函数，你改完再 `return` 回去，然后才真正发出去。

```
getBanners() 调用 hyRequest.get({ url: '/api/banner' })
    ↓
requestInterceptor 执行
    ↓
从 localStorage 读 token → 写入 config.headers.Authorization
    ↓
return config
    ↓
axios 真正发出请求（请求头带 Authorization: Bearer xxx）
```

#### `InternalAxiosRequestConfig` 是什么？

一次请求的完整配置对象，常见字段：

```ts
{
  url: '/api/banner',     // 请求地址
  method: 'GET',          // 请求方法
  headers: { ... },       // 请求头（token 加在这里）
  params: { ... },        // URL 查询参数
  data: { ... },          // 请求体（POST 用）
  timeout: 10000,
  baseURL: 'http://...',
}
```

#### 四个拦截器分工

| 拦截器                     | 触发时机               | 项目里的作用                                   |
| -------------------------- | ---------------------- | ---------------------------------------------- |
| `requestInterceptor`       | 请求发出**前**         | 自动注入 `Authorization: Bearer <token>`       |
| `requestInterceptorCatch`  | 请求配置阶段**出错**   | `Promise.reject(error)` 抛出错误               |
| `responseInterceptor`      | 收到响应**后**         | 判断 `code !== 200` 则 reject，否则 return res |
| `responseInterceptorCatch` | 网络错误（断网、超时） | 打印日志并 `Promise.reject(error)`             |

#### 实际配置代码

```ts
const hyRequest = new HYRequest({
  interceptors: {
    requestInterceptor: (config) => {
      const token = getAccessToken();
      if (token) {
        config.headers.Authorization = `Bearer ${token}`;
      }
      return config; // 必须 return，axios 才用修改后的配置发请求
    },
    requestInterceptorCatch: (error) => Promise.reject(error),

    responseInterceptor: (res) => {
      const data = res.data as IDataType;
      if (data.code !== 200) {
        return Promise.reject(data);
      }
      return res;
    },
    responseInterceptorCatch: (error) => Promise.reject(error),
  },
});
```

#### 为什么不用 `const x: IHYInterceptors =` 显式标注？

上面 `interceptors: { ... }` 没有写 `: IHYInterceptors`，但**已经在用接口约束**了。

**原理：TypeScript 类型推断**

```ts
constructor(config: IHYRequestConfig) { ... }
//                    ↑ 参数要求 interceptors 符合 IHYInterceptors

const hyRequest = new HYRequest({
  interceptors: { requestInterceptor: (config) => { ... } },
  //              ↑ TS 自动按 IHYInterceptors 检查
});
```

**两种写法，效果一样：**

```ts
// 写法 A：隐式推断（当前项目，更简洁）
const hyRequest = new HYRequest({
  interceptors: {
    requestInterceptor: (config) => {
      return config;
    },
  },
});

// 写法 B：显式标注（复用场景更清晰）
const myInterceptors: IHYInterceptors = {
  requestInterceptor: (config) => {
    return config;
  },
};
const hyRequest = new HYRequest({ interceptors: myInterceptors });
```

写错了 TS 照样报错（字段名拼错、参数类型不对），说明接口约束一直在生效。

| 场景                   | 建议                              |
| ---------------------- | --------------------------------- |
| 直接传给构造函数       | 不写也行，TS 自动检查             |
| 拦截器要复用到多个实例 | 建议 `const x: IHYInterceptors =` |
| 想 IDE 更明确提示字段  | 显式写类型更清晰                  |

### 使用示例

```ts
import hyRequest from './request';
import type { IDataType, IBanner } from './types';

// GET 请求
export function getBanners() {
  return hyRequest.get<IDataType<IBanner[]>>({
    url: '/api/banner',
  });
}

// POST 请求
export function createGame(data: { name: string }) {
  return hyRequest.post<IDataType<IGameItem>>({
    url: '/api/games',
    data,
  });
}
```

---

## 五、业务 API 模块

每个业务模块一个文件，只放 API 函数，不写组件逻辑：

```ts
// service/home.ts
import hyRequest from './request';

export function getBanners() {
  return hyRequest.get<IDataType<IBanner[]>>({ url: '/api/banner' });
}

export function getGameList() {
  return hyRequest.get<IDataType<IGameItem[]>>({ url: '/api/games' });
}
```

```ts
// service/recommend.ts
export function getRecommendData() {
  return hyRequest.get<IDataType<IRecommendData>>({ url: '/api/recommend' });
}
```

---

## 六、mock 虚拟数据

开发环境无后端时，用 `mockjs` 拦截 URL 返回假数据。

```
src/mock/index.ts   ← 开发环境在 index.tsx 中引入
```

已拦截的接口：

| URL              | 方法 | 返回        |
| ---------------- | ---- | ----------- |
| `/api/banner`    | GET  | Banner 列表 |
| `/api/games`     | GET  | 游戏列表    |
| `/api/recommend` | GET  | 推荐页数据  |

新增 mock 接口：在 `mock/index.ts` 中追加 `Mock.mock(...)` 即可。

---

## 七、与 store 配合（异步请求）

组件不直接调 service，通过 `createAsyncThunk` 走 store。完整原理见 `src/store/README.md` **「五、异步请求：createAsyncThunk 详解」**。

### 数据流

```
页面 dispatch(fetchHomeData())
    → RTK 自动 pending（loading = true）
    → thunk 调用 service/home.ts 的 getBanners()、getGameList()
    → HYRequest(axios) 发请求 → mock 拦截返回数据
    → RTK 自动 fulfilled → extraReducers 写入 state
    → 页面 useAppSelector 渲染
```

### store 侧代码

```tsx
// store/modules/home.ts
export const fetchHomeData = createAsyncThunk(
  'home/fetchHomeData',
  async () => {
    const [bannerRes, gameRes] = await Promise.all([
      getBanners(),
      getGameList(),
    ]);
    return { banners: bannerRes.data, games: gameRes.data };
  },
);

// extraReducers 监听 RTK 自动生成的 pending/fulfilled/rejected
extraReducers: (builder) => {
  builder
    .addCase(fetchHomeData.pending, (state) => { state.loading = true; })
    .addCase(fetchHomeData.fulfilled, (state, action) => {
      state.banners = action.payload.banners;
      state.games = action.payload.games;
    })
    .addCase(fetchHomeData.rejected, (state, action) => {
      state.error = action.error.message || '请求失败';
    });
},
```

### 页面侧代码

```tsx
// views/Home/index.tsx
useEffect(() => {
  dispatch(fetchHomeData());
}, [dispatch]);

const { banners, games, loading } = useAppSelector((state) => state.home);
```

### 关键概念速查

| 概念               | 说明                                                         |
| ------------------ | ------------------------------------------------------------ |
| `createAsyncThunk` | 创建异步任务，RTK 自动生成 pending/fulfilled/rejected        |
| `pending`          | `dispatch` 瞬间触发，`loading = true`                        |
| `fulfilled`        | 请求成功，`action.payload` = async 函数 return 的数据        |
| `rejected`         | 请求失败，`action.error` 含错误信息                          |
| `extraReducers`    | 监听 thunk 自动生成的 action（不是 `reducers` 里自己定义的） |
| `Promise.all`      | 并行发多个请求，等全部完成                                   |

---

## 八、新增 API 流程

1. 在 `service/types.ts` 定义数据类型
2. 在 `service/<module>.ts` 写 API 函数（调用 `hyRequest.get/post/...`）
3. 在 `mock/index.ts` 添加对应 mock（开发阶段）
4. 在 `store/modules/<module>.ts` 写 `createAsyncThunk`
5. 在页面 `dispatch(thunk)` + `useAppSelector` 渲染

---

## 九、相关文件

| 文件                             | 说明                                              |
| -------------------------------- | ------------------------------------------------- |
| `src/mock/index.ts`              | mock 虚拟数据                                     |
| `src/utils/storage.ts`           | token 读写                                        |
| `src/store/modules/home.ts`      | 首页数据 thunk                                    |
| `src/store/modules/recommend.ts` | 推荐页数据 thunk                                  |
| `src/views/Home/index.tsx`       | 首页展示 mock 数据                                |
| `src/views/Recommend/index.tsx`  | 推荐页                                            |
| `src/store/README.md`            | createAsyncThunk、pending/fulfilled/rejected 详解 |
| `PROJECT_STRUCTURE.md`           | 全项目目录规范                                    |
