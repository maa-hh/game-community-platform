import { createSlice, PayloadAction } from '@reduxjs/toolkit';

// counter 模块的 state 类型
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
