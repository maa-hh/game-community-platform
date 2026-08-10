import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';
import { getRecommendData } from '@/service/recommend';
import type { IBanner, IGameItem } from '@/service/types';

export const fetchRecommendData = createAsyncThunk(
  'recommend/fetchRecommendData',
  async () => {
    const res = await getRecommendData();
    return res.data;
  },
);

interface IRecommendState {
  banners: IBanner[];
  hotGames: IGameItem[];
  loading: boolean;
  error: string | null;
}

const initialState: IRecommendState = {
  banners: [],
  hotGames: [],
  loading: false,
  error: null,
};

const recommendSlice = createSlice({
  name: 'recommend',
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(fetchRecommendData.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(fetchRecommendData.fulfilled, (state, action) => {
        state.loading = false;
        state.banners = action.payload.banners;
        state.hotGames = action.payload.hotGames;
      })
      .addCase(fetchRecommendData.rejected, (state, action) => {
        state.loading = false;
        state.error = action.error.message || '请求失败';
      });
  },
});

export default recommendSlice.reducer;
