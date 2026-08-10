# `store/` 全局状态管理说明

本目录使用 **Redux Toolkit** 管理跨页面共享的全局状态。

---

## 一、目录结构

```
store/
├── index.ts              # store 入口 + 类型化 hooks
├── modules/
│   ├── counter.ts        # counter 模块（同步 action 示例）
│   ├── home.ts           # home 模块（异步 thunk 示例）
│   └── recommend.ts      # recommend 模块（异步 thunk 示例）
└── README.md
```

---

## 二、store 入口 `index.ts`

### 完整代码

```tsx
import { configureStore } from '@reduxjs/toolkit';
import {
  useDispatch,
  useSelector,
  shallowEqual,
  TypedUseSelectorHook,
} from 'react-redux';
import counterReducer from './modules/counter';

const store = configureStore({
  reducer: {
    counter: counterReducer,
  },
});

// 自动推导 RootState 类型
type GetStateFnType = typeof store.getState;
export type IRootState = ReturnType<GetStateFnType>;
type DispatchType = typeof store.dispatch;

// 类型化 hooks
export const useAppSelector: TypedUseSelectorHook<IRootState> = useSelector;
export const useAppDispatch: () => DispatchType = useDispatch;
export const appShallowEqual = shallowEqual;

export default store;
```

### 关键点说明

#### 1. `configureStore` — 创建 store

Redux Toolkit 推荐的 store 创建方式，内置：

- Redux DevTools 支持
- thunk 中间件（异步 action）
- 开发环境序列化检查

```tsx
const store = configureStore({
  reducer: {
    counter: counterReducer, // state.counter 由 counterReducer 管理
  },
});
```

#### 2. `IRootState` — 自动推导 state 类型

```tsx
type GetStateFnType = typeof store.getState;
export type IRootState = ReturnType<GetStateFnType>;
```

**好处：** 不用手写 state 类型，新增 reducer 后 `IRootState` 自动更新。

```tsx
// IRootState 自动包含：
{
  counter: {
    count: number;
    message: string;
  }
}
```

> 类型推导的完整原理见下方 **「三、类型推导详解」**。

#### 3. `useAppSelector` — 类型化的 useSelector

```tsx
export const useAppSelector: TypedUseSelectorHook<IRootState> = useSelector;
```

**为什么不用原生 `useSelector`？**

```tsx
// ❌ 原生：state 类型是 any，没有自动补全
const count = useSelector((state) => state.counter.count);

// ✅ 类型化：state 自动是 IRootState，有完整类型提示
const count = useAppSelector((state) => state.counter.count);
```

#### 4. `useAppDispatch` — 类型化的 useDispatch

```tsx
export const useAppDispatch: () => DispatchType = useDispatch;
```

确保 `dispatch` 能正确派发 slice 的 action，支持 thunk 类型推导。

> `DispatchType` 的推导原理见下方 **「三、类型推导详解」**。

#### 5. `appShallowEqual` — 浅比较函数

配合 `useAppSelector` 选取多个字段时使用，详见下方「shallowEqual 说明」。

---

## 三、类型推导详解

store 入口有两行类型推导代码，初学者容易困惑。本节用直白的方式解释它们究竟推导出了什么。

### 3.1 核心代码

```tsx
type GetStateFnType = typeof store.getState;
export type IRootState = ReturnType<GetStateFnType>;
type DispatchType = typeof store.dispatch;
```

### 3.2 `IRootState` — 推导出的是什么？

#### 最终结果就是 state 的结构类型

上面两行代码，最终 `IRootState` 完全等价于手写：

```ts
type IRootState = {
  counter: {
    count: number;
    message: string;
  };
};
```

也就是组件里 `useAppSelector((state) => state.counter.count)` 中，那个 `state` 的完整形状。

**state 类型就是 state 本身**——我们只是用 TypeScript 自动推导出它长什么样，避免手写、避免和 `configureStore` 不同步。

#### 为什么不手写，要从 `getState` 绕一圈？

可以手写，但麻烦且容易过时：

```ts
// 手写方式 — 能用，但要自己维护
interface IRootState {
  counter: { count: number; message: string };
}
```

问题：以后在 `configureStore` 里加了 `user` reducer，你要**记得**去改 `IRootState`，忘了就类型对不上。

用 `getState` 推导的好处：

```
configureStore 里注册了哪些 reducer
    → getState() 返回的 state 自动包含这些模块
    → IRootState 自动跟着变
    → 不用手写、不用同步
```

#### 三步拆解

**第 1 步：`store.getState` 是什么？**

`getState` 是一个**函数**，调用后返回当前 state 对象：

```ts
store.getState();
// 运行时返回：
// { counter: { count: 0, message: 'Hello Redux' } }
```

**第 2 步：`typeof store.getState` 拿到什么？**

拿到的是**这个函数的类型**（不是 state 本身）：

```ts
type GetStateFnType = typeof store.getState;

// 等价于：
type GetStateFnType = () => {
  counter: {
    count: number;
    message: string;
  };
};
```

注意：这是**函数类型**，返回值才是 state 的结构。

**第 3 步：`ReturnType<>` 取出返回值类型**

`ReturnType` 是 TypeScript 内置工具类型，作用：**从一个函数类型里，取出它的返回值类型**。

```ts
type IRootState = ReturnType<GetStateFnType>;

// 等价于：
type IRootState = {
  counter: {
    count: number;
    message: string;
  };
};
```

#### 推导流程图

```
store.getState          ← 函数（调用后返回 state 对象）
       ↓ typeof
GetStateFnType          ← 函数的类型：() => { counter: {...} }
       ↓ ReturnType
IRootState              ← 函数的返回值类型 = state 的结构类型
       ↓
useAppSelector 里的 state 参数自动是这个类型
```

#### 和组件里的 `state` 是什么关系？

完全一样的东西，只是用在不同地方：

```tsx
// CounterPanel 里
useAppSelector((state) => state.counter.count);
//                 ↑
//            这个 state 的类型就是 IRootState
```

等价于显式标注：

```ts
(state: IRootState) => state.counter.count;
//       ↑
//  { counter: { count: number; message: string } }
```

#### 为什么不直接写 `type IRootState = typeof store`？

因为 `store` 是整个 store 对象（包含 `dispatch`、`getState`、`subscribe` 等），`typeof store` 拿到的是 store 实例的类型，不是 state 的结构。

我们要的是 **state 数据的形状**，所以要从 `getState()` 的**返回值**里取。

#### 以后加新模块会自动更新

```ts
const store = configureStore({
  reducer: {
    counter: counterReducer,
    user: userReducer, // 新增
  },
});

// IRootState 自动变成：
// {
//   counter: { count: number; message: string };
//   user: { name: string; avatar: string };
// }
```

不用改 `IRootState` 的定义，类型会自动跟上。

---

### 3.3 `DispatchType` — 推导出的是什么？

#### 和 `IRootState` 的对比

|              | `getState`                          | `dispatch`                      |
| ------------ | ----------------------------------- | ------------------------------- |
| 是什么       | 函数，**返回** state                | 函数，**接收** action           |
| 我们关心什么 | 它**返回什么**（state 结构）        | 它**能接收什么**（哪些 action） |
| 类型写法     | `ReturnType<typeof store.getState>` | `typeof store.dispatch`         |
| 推导结果     | state 的数据结构                    | dispatch 函数的签名             |

```
getState:  () => IRootState     → 用 ReturnType 取返回值 → IRootState
dispatch:  (action) => void    → 用 typeof 取函数本身   → DispatchType
```

**关键区别：** `getState` 用 `ReturnType` 是因为我们关心**返回值**；`dispatch` 用 `typeof` 是因为我们关心**函数本身能传什么参数**。

#### `typeof store.dispatch` 拿到什么？

拿到的是 **dispatch 函数本身的类型**，大致等价于：

```ts
type DispatchType = (action: /* 允许的 action 类型 */) => void;
```

意思是：这是一个函数，接收某种 action，返回值一般是 `void`（thunk 时是 Promise）。

#### 对你项目来说，能传什么？

当前 store 只有 `counter` 模块，所以 `dispatch` 能接收：

```ts
// 1. slice 自动生成的 action creator 的返回值
dispatch(increment());
// increment() 返回 { type: 'counter/increment' }

dispatch(decrement());
// decrement() 返回 { type: 'counter/decrement' }

dispatch(setMessage('新消息'));
// setMessage('新消息') 返回 { type: 'counter/setMessage', payload: '新消息' }

// 2. 普通 action 对象（也可以，但不推荐手写）
dispatch({ type: 'counter/increment' });
```

TypeScript 会检查你传的 action 是否合法：

```tsx
dispatch(increment()); // ✅
dispatch({ type: 'xxx' }); // ❌ 类型报错，没有这个 action
dispatch('hello'); // ❌ 类型报错
```

#### 为什么需要 `useAppDispatch`？

和 `useAppSelector` 一样，是为了类型安全：

```tsx
// ❌ 原生 useDispatch：dispatch 类型是通用的，什么都能传，没有检查
const dispatch = useDispatch();
dispatch(anything); // 不报错，但运行可能出问题

// ✅ useAppDispatch：dispatch 类型是 DispatchType，只接受合法的 action
const dispatch = useAppDispatch();
dispatch(increment()); // ✅ 有类型检查和自动补全
```

#### 以后加了新模块会自动更新

```ts
const store = configureStore({
  reducer: {
    counter: counterReducer,
    user: userReducer, // 新增 user 模块
  },
});

// DispatchType 自动知道可以派发 user 模块的 action：
dispatch(setUserName('Alice')); // ✅ 自动有类型
```

不用手动维护 dispatch 能传哪些 action，和 `configureStore` 注册的 reducer 保持同步。

#### 异步 thunk 也支持

Redux Toolkit 的 `configureStore` 内置了 thunk 中间件，所以 `DispatchType` 还支持传**异步函数**：

```ts
// 假设你写了一个 thunk
const fetchUser = () => async (dispatch, getState) => {
  const res = await fetch('/api/user');
  dispatch(setUser(await res.json()));
};

dispatch(fetchUser()); // ✅ DispatchType 也支持这种异步函数
```

这也是 `typeof store.dispatch` 比手写类型更强的地方——它自动包含了 thunk 的类型。

---

### 3.4 一句话总结

| 类型           | 推导方式                            | 得到什么                               | 用在哪                           |
| -------------- | ----------------------------------- | -------------------------------------- | -------------------------------- |
| `IRootState`   | `ReturnType<typeof store.getState>` | state 的完整结构                       | `useAppSelector` 的 `state` 参数 |
| `DispatchType` | `typeof store.dispatch`             | dispatch 函数的签名（能传哪些 action） | `useAppDispatch` 的返回值        |

> **`getState` 是返回 state 的函数，`ReturnType` 取出它返回值的类型，那就是 state 的结构。**
> **`dispatch` 是派发 action 的函数，`typeof` 取出函数本身的类型，告诉你能传哪些 action。**
> 两者都和组件里实际用到的 `state` / `dispatch` 是同一个东西，只是用推导避免手写、避免和 store 配置不同步。

---

## 四、模块写法 `modules/counter.ts`

### 完整代码

```tsx
import { createSlice, PayloadAction } from '@reduxjs/toolkit';

export interface ICounterState {
  count: number;
  message: string;
}

const initialState: ICounterState = {
  count: 0,
  message: 'Hello Redux',
};

const counterSlice = createSlice({
  name: 'counter',
  initialState,
  reducers: {
    increment: (state) => {
      state.count += 1;
    },
    decrement: (state) => {
      state.count -= 1;
    },
    setMessage: (state, action: PayloadAction<string>) => {
      state.message = action.payload;
    },
  },
});

export const { increment, decrement, setMessage } = counterSlice.actions;
export default counterSlice.reducer;
```

> 同步 action 用 `reducers` 定义。网络请求等异步场景用 `createAsyncThunk` + `extraReducers`，详见 **「五、异步请求：createAsyncThunk 详解」**。

### createSlice 做了什么

| 自动生成   | 说明                                           |
| ---------- | ---------------------------------------------- |
| `reducer`  | 注册到 store，管理 `state.counter`             |
| `actions`  | `increment()`、`decrement()` 等 action creator |
| Immer 集成 | 可直接 `state.count += 1`，底层自动不可变更新  |

### 在组件中使用

```tsx
import { useAppDispatch } from '@/store';
import { increment, decrement } from '@/store/modules/counter';

const dispatch = useAppDispatch();
dispatch(increment()); // count + 1
dispatch(decrement()); // count - 1
```

---

## 五、异步请求：createAsyncThunk 详解

网络请求不走组件里的 `useEffect + fetch`，而是通过 `createAsyncThunk` 交给 store 统一管理。以 `modules/home.ts` 为例。

### 5.1 完整代码

```tsx
// 1. 创建异步任务
export const fetchHomeData = createAsyncThunk(
  'home/fetchHomeData',
  async () => {
    const [bannerRes, gameRes] = await Promise.all([
      getBanners(),
      getGameList(),
    ]);
    return {
      banners: bannerRes.data,
      games: gameRes.data,
    };
  },
);

// 2. slice 监听 thunk 的三种状态
const homeSlice = createSlice({
  name: 'home',
  initialState: { banners: [], games: [], loading: false, error: null },
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(fetchHomeData.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(fetchHomeData.fulfilled, (state, action) => {
        state.loading = false;
        state.banners = action.payload.banners;
        state.games = action.payload.games;
      })
      .addCase(fetchHomeData.rejected, (state, action) => {
        state.loading = false;
        state.error = action.error.message || '请求失败';
      });
  },
});
```

### 5.2 `createAsyncThunk` 做了什么？

```tsx
export const fetchHomeData = createAsyncThunk(
  'home/fetchHomeData',   // 任务名（关键）
  async () => { ... },    // 真正干活的异步函数
);
```

| 部分                   | 作用                                               |
| ---------------------- | -------------------------------------------------- |
| `createAsyncThunk`     | 创建一个"异步任务"，能发网络请求并把结果写入 store |
| `'home/fetchHomeData'` | 任务唯一标识，RTK 据此自动生成 3 个 action         |
| `async () => { ... }`  | 具体逻辑：调 service 发请求，`return` 数据         |

Redux 普通 `reducer` 只能**同步**改 state，不能 `await`。`createAsyncThunk` 专门处理异步场景。

### 5.3 `async` 函数里在干什么？

```tsx
const [bannerRes, gameRes] = await Promise.all([
  getBanners(), // GET /api/banner
  getGameList(), // GET /api/games
]);
return {
  banners: bannerRes.data,
  games: gameRes.data,
};
```

- `Promise.all([...])`：**同时**发两个请求，等两个都完成（并行，比串行快）
- `return { banners, games }`：成功时返回的数据，会成为 `fulfilled` 时的 `action.payload`

### 5.4 完整数据流

```
Home 页面渲染
  useEffect(() => dispatch(fetchHomeData()))
       ↓
RTK 自动触发 pending → loading = true
       ↓
执行 async 函数
  service/home.ts → HYRequest(axios) → mock 返回数据
       ↓
成功 return 数据 → RTK 自动触发 fulfilled
  → banners/games 写入 state, loading = false
       ↓
页面 useAppSelector(state => state.home) 读到数据，重新渲染
```

### 5.5 为什么不直接在组件里请求？

```tsx
// ❌ 组件里直接请求（能跑，但不推荐）
useEffect(() => {
  getBanners().then((res) => setBanners(res.data));
}, []);

// ✅ 通过 store 请求（当前做法）
useEffect(() => {
  dispatch(fetchHomeData());
}, [dispatch]);
const { banners, games, loading } = useAppSelector((state) => state.home);
```

| 好处     | 说明                                                |
| -------- | --------------------------------------------------- |
| 数据共享 | 多个组件都能读 `state.home`，不用层层传 props       |
| 状态统一 | `loading`、`error` 集中管理                         |
| 逻辑分离 | 组件只管渲染，请求逻辑放 store                      |
| 可复用   | 任何地方 `dispatch(fetchHomeData())` 都能重新拉数据 |

---

### 5.6 pending / fulfilled / rejected 从哪里来？

**这三个状态不是你手写的**，是 `createAsyncThunk` 根据任务名 **自动生成** 的。

```tsx
createAsyncThunk('home/fetchHomeData', async () => { ... });
```

RTK 自动注册 3 个 action：

| 自动生成的 action type         | 什么时候触发                            |
| ------------------------------ | --------------------------------------- |
| `home/fetchHomeData/pending`   | 调用 `dispatch(fetchHomeData())` 的瞬间 |
| `home/fetchHomeData/fulfilled` | async 函数成功 `return` 之后            |
| `home/fetchHomeData/rejected`  | async 函数抛错 / 请求失败               |

时间线：

```
dispatch(fetchHomeData())
    │
    ├─ 立刻触发 → pending    （请求刚开始，loading = true）
    │
    ├─ async 函数执行中...
    │   getBanners() + getGameList()
    │
    ├─ 成功 return 数据 → fulfilled  （写入 banners/games）
    │
    └─ 抛错 / reject   → rejected    （error = '请求失败'）
```

对应 `extraReducers` 里的处理：

| 状态        | 触发时机 | state 变化                                    |
| ----------- | -------- | --------------------------------------------- |
| `pending`   | 请求开始 | `loading = true`, `error = null`              |
| `fulfilled` | 请求成功 | `loading = false`, 写入 `action.payload` 数据 |
| `rejected`  | 请求失败 | `loading = false`, `error = '请求失败'`       |

`action.payload` 就是 async 函数 `return { banners, games }` 返回的对象。

---

### 5.7 为什么在 `extraReducers` 里，而不是 `reducers` 里？

`createSlice` 有两种 reducer：

```tsx
const homeSlice = createSlice({
  reducers: {}, // 自己定义的同步 action
  extraReducers: {}, // 外部定义的 action（thunk 自动生成的）
});
```

|                 | `reducers`                     | `extraReducers`                                      |
| --------------- | ------------------------------ | ---------------------------------------------------- |
| 谁定义的 action | **你自己**在 slice 里写        | **外部**定义（thunk 自动生成、其他 slice 的 action） |
| 典型用途        | 同步改 state，如 `increment()` | 响应 thunk 的 pending/fulfilled/rejected             |
| action 命名     | `home/clearError`              | `home/fetchHomeData/pending` 等                      |

`pending` / `fulfilled` / `rejected` 是 `createAsyncThunk` **在外面自动创建**的，不是你在 `reducers` 里定义的，所以必须用 `extraReducers` 来监听：

```tsx
// reducers — 听自己人指挥（自己定义的 action）
reducers: {
  clearError(state) {
    state.error = null;
  },
}

// extraReducers — 听外部通知（thunk 自动生成的 action）
extraReducers: (builder) => {
  builder.addCase(fetchHomeData.pending, ...)
         .addCase(fetchHomeData.fulfilled, ...)
         .addCase(fetchHomeData.rejected, ...);
}
```

### 5.8 `addCase` 做了什么？

```tsx
.addCase(fetchHomeData.fulfilled, (state, action) => {
  state.banners = action.payload.banners;
})
```

含义：**当收到 `home/fetchHomeData/fulfilled` 这个 action 时，执行这个函数更新 state。**

- `fetchHomeData.fulfilled`：RTK 提供的 action 引用，等价于字符串 `'home/fetchHomeData/fulfilled'`
- `action.payload`：async 函数 `return` 的数据

### 5.9 完整对应关系图

```
createAsyncThunk('home/fetchHomeData', async () => {...})
        │
        │ 自动生成 3 个 action
        ▼
┌─────────────────────────────────────────────────┐
│  home/fetchHomeData/pending                     │
│  home/fetchHomeData/fulfilled  + payload 数据  │
│  home/fetchHomeData/rejected   + error 信息    │
└─────────────────────────────────────────────────┘
        │
        │ extraReducers 监听这 3 个 action
        ▼
┌─────────────────────────────────────────────────┐
│  pending   → state.loading = true               │
│  fulfilled → state.banners/games = payload    │
│  rejected  → state.error = '请求失败'          │
└─────────────────────────────────────────────────┘
        │
        │ useAppSelector 读取
        ▼
      Home 页面重新渲染
```

### 5.10 在页面中使用

```tsx
// views/Home/index.tsx
import { fetchHomeData } from '@/store/modules/home';

function Home() {
  const dispatch = useAppDispatch();
  const { banners, games, loading, error } = useAppSelector(
    (state) => state.home,
  );

  useEffect(() => {
    dispatch(fetchHomeData());
  }, [dispatch]);

  if (loading) return <p>加载中…</p>;
  if (error) return <p>错误：{error}</p>;
  // 渲染 banners、games ...
}
```

> **一句话：`fetchHomeData` 是异步任务，RTK 自动生成 pending/fulfilled/rejected 三个 action，`extraReducers` 监听它们来更新 loading 和数据。页面只负责 `dispatch` + `useAppSelector`。**

---

## 六、useAppSelector + shallowEqual

### 问题：为什么选取对象时要加 shallowEqual？

```tsx
// 这种写法每次都会返回新对象 { count, message }
const { count, message } = useAppSelector((state) => ({
  count: state.counter.count,
  message: state.counter.message,
}));
```

```
每次 Redux state 变化（哪怕别的模块变了）
    → selector 执行，返回新对象 { count: 0, message: '...' }
    → 新对象 !== 旧对象（引用不同）
    → 组件重渲染 ❌（即使 count 和 message 没变）
```

### 解决：传入 shallowEqual

```tsx
import { useAppSelector, appShallowEqual } from '@/store';

const { count, message } = useAppSelector(
  (state) => ({
    count: state.counter.count,
    message: state.counter.message,
  }),
  appShallowEqual, // 浅比较对象内的每个字段
);
```

```
Redux state 变化
    → selector 返回新对象
    → shallowEqual 比较：count 没变？message 没变？
    → 都没变 → 不重渲染 ✅
    → 有变化 → 重渲染 ✅
```

### 三种选取方式对比

```tsx
// 方式 1：选一个字段 — 不需要 shallowEqual
const count = useAppSelector((state) => state.counter.count);

// 方式 2：选多个字段返回对象 — 需要 shallowEqual
const { count, message } = useAppSelector(
  (state) => ({
    count: state.counter.count,
    message: state.counter.message,
  }),
  appShallowEqual,
);

// 方式 3：多次调用 — 不需要 shallowEqual，但代码略冗长
const count = useAppSelector((state) => state.counter.count);
const message = useAppSelector((state) => state.counter.message);
```

| 方式                | shallowEqual | 适用               |
| ------------------- | ------------ | ------------------ |
| 选单个字段          | 不需要       | 最常用，最简单     |
| 选多个字段返回对象  | **需要**     | 解构方便，注意性能 |
| 多次 useAppSelector | 不需要       | 字段少时推荐       |

---

## 七、接入应用

在 `App.tsx` 用 `<Provider>` 包裹整个应用：

```tsx
import { Provider } from 'react-redux';
import store from '@/store';

function App() {
  return (
    <Provider store={store}>
      <AppRouter />
    </Provider>
  );
}
```

之后任意子组件都可以使用 `useAppSelector` / `useAppDispatch`。

---

## 八、新增 store 模块流程

1. 在 `store/modules/<name>.ts` 用 `createSlice` 创建模块
2. 在 `store/index.ts` 的 `reducer` 中注册
3. `IRootState` 自动更新，无需手动改类型
4. 在组件中通过 `useAppSelector` / `useAppDispatch` 使用

```tsx
// store/index.ts 注册新模块
import userReducer from './modules/user';

const store = configureStore({
  reducer: {
    counter: counterReducer,
    user: userReducer, // 新增
  },
});
// IRootState 自动包含 state.user
```

---

## 九、使用规范

- **全局共享状态**放 store；组件局部状态用 `useState`
- 按业务模块拆分 slice，不要一个巨大 reducer
- 组件通过 `useAppDispatch` 派发 action，不要直接改 state
- 选取多个字段时记得加 `appShallowEqual`
- 不要把视图逻辑写进 reducer

---

## 十、相关文件

| 文件                                    | 说明                                    |
| --------------------------------------- | --------------------------------------- |
| `src/components/CounterPanel/index.tsx` | 演示 useAppSelector + shallowEqual      |
| `src/store/modules/home.ts`             | 演示 createAsyncThunk + extraReducers   |
| `src/store/modules/recommend.ts`        | 推荐页异步 thunk 示例                   |
| `src/service/home.ts`                   | 首页 API（被 home thunk 调用）          |
| `src/views/Home/index.tsx`              | 演示 dispatch(fetchHomeData) + 渲染数据 |
| `src/views/Recommend/index.tsx`         | 推荐页异步数据展示                      |
| `src/App.tsx`                           | Provider 接入点                         |
| `src/service/README.md`                 | axios 封装与 mock 说明                  |
| `PROJECT_STRUCTURE.md`                  | 全项目目录规范                          |
