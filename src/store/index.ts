import { combineReducers, configureStore } from '@reduxjs/toolkit';
import { setupListeners } from '@reduxjs/toolkit/query';
import {
  useDispatch,
  useSelector,
  shallowEqual,
  TypedUseSelectorHook,
} from 'react-redux';
import authReducer from './modules/auth';
import articleProgressReducer from './modules/articleProgress';
import notificationReducer from './modules/notification';
import postInteractionReducer from './modules/postInteraction';
import profileRealtimeReducer from './modules/profileRealtime';
import { serverApi } from './services/serverApi';

const appReducer = combineReducers({
  auth: authReducer,
  articleProgress: articleProgressReducer,
  notification: notificationReducer,
  postInteraction: postInteractionReducer,
  profileRealtime: profileRealtimeReducer,
  [serverApi.reducerPath]: serverApi.reducer,
});

const store = configureStore({
  reducer: appReducer,
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware().concat(serverApi.middleware),
});

// 让 RTK Query 在窗口重新聚焦或网络恢复时按订阅状态执行刷新。
setupListeners(store.dispatch);

// 从 store 自动推导 RootState 类型，state 结构变化时类型自动同步
type GetStateFnType = typeof store.getState;
export type IRootState = ReturnType<GetStateFnType>;
type DispatchType = typeof store.dispatch;

// 类型化的 useSelector：组件里直接用，无需每次手动标注 state 类型
export const useAppSelector: TypedUseSelectorHook<IRootState> = useSelector;

// 类型化的 useDispatch：支持 thunk 等中间件的正确类型推导
export const useAppDispatch: () => DispatchType = useDispatch;

// 导出 shallowEqual，配合 useAppSelector 选取对象时避免多余重渲染
export const appShallowEqual = shallowEqual;

export default store;
