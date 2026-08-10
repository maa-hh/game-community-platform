import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';
import { getBanners, getGameList } from '@/service/home';
import type { IBanner, IGameItem } from '@/service/types';

// 异步 thunk：组件 dispatch 后由 store 发起网络请求
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

interface IHomeState {
  banners: IBanner[];
  games: IGameItem[];
  loading: boolean;
  error: string | null;
}

const initialState: IHomeState = {
  banners: [],
  games: [],
  loading: false,
  error: null,
};

const homeSlice = createSlice({
  name: 'home',
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(fetchHomeData.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(fetchHomeData.fulfilled, (state, action) => {
        state.loading = false;
        state.banners = action.payload.banners ?? [];
        state.games = action.payload.games ?? [];
      })
      .addCase(fetchHomeData.rejected, (state, action) => {
        state.loading = false;
        state.error = action.error.message || '请求失败';
      });
  },
});

export default homeSlice.reducer;
