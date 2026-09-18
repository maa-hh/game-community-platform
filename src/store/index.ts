import { combineReducers, configureStore } from '@reduxjs/toolkit';
import {
  useDispatch,
  useSelector,
  shallowEqual,
  TypedUseSelectorHook,
} from 'react-redux';
import counterReducer from './modules/counter';
import homeReducer from './modules/home';
import authReducer from './modules/auth';
import articleProgressReducer from './modules/articleProgress';
import notificationReducer from './modules/notification';
import postInteractionReducer from './modules/postInteraction';
import profileRealtimeReducer from './modules/profileRealtime';

const appReducer = combineReducers({
  counter: counterReducer,
  home: homeReducer,
  auth: authReducer,
  articleProgress: articleProgressReducer,
  notification: notificationReducer,
  postInteraction: postInteractionReducer,
  profileRealtime: profileRealtimeReducer,
});

const store = configureStore({
  reducer: appReducer,
});

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
