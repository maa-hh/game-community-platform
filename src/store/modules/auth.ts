import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';
import {
  loginByEmail,
  registerByEmail,
  sendVerifyCode,
  resetPasswordByEmail,
  logoutApi,
} from '@/service/auth';
import { getCurrentUserApi } from '@/service/profile';
import type {
  ILoginParams,
  IRegisterParams,
  IResetPasswordParams,
  ISendCodeParams,
  IUserInfo,
  IAuthResult,
} from '@/service/types';
import { normalizeUserInfo } from '@/service/types';
import {
  setAccessAuth,
  setUserInfo,
  getUserInfo,
  getAccessToken,
  clearAuth,
  removeAccessToken,
  removeAccessExpireAt,
} from '@/utils/storage';
import { formatApiError } from '@/utils/apiError';
import { resetAuthRefreshState } from '@/service/request';
import { clearPostInteractions } from './postInteraction';

function getErrorMessage(error: unknown, prefix: string): string {
  return formatApiError(prefix, error);
}

function persistAuthResult(data: IAuthResult) {
  resetAuthRefreshState();
  const prev = getUserInfo();
  const sameUser = prev?.accountId === data.user.accountId;
  const user = normalizeUserInfo(data.user, sameUser ? prev : null);
  setAccessAuth({
    accessToken: data.accessToken,
    accessExpiresIn: data.accessExpiresIn,
  });
  setUserInfo(user);
  return user;
}

export const loginAction = createAsyncThunk(
  'auth/login',
  async (params: ILoginParams, { rejectWithValue }) => {
    try {
      const res = await loginByEmail(params);
      return res.data;
    } catch (error) {
      return rejectWithValue(getErrorMessage(error, '登录失败'));
    }
  },
);

/**
 * 注册：先注册账号，再走登录接口拿双 token（两次真实网络请求）
 */
export const registerAction = createAsyncThunk(
  'auth/register',
  async (params: IRegisterParams, { rejectWithValue }) => {
    try {
      await registerByEmail(params);
      const loginRes = await loginByEmail({
        email: params.email,
        password: params.password,
      });
      return loginRes.data;
    } catch (error) {
      return rejectWithValue(getErrorMessage(error, '注册失败'));
    }
  },
);

/** 发送邮箱验证码 */
export const sendCodeAction = createAsyncThunk(
  'auth/sendCode',
  async (params: ISendCodeParams, { rejectWithValue }) => {
    try {
      const res = await sendVerifyCode(params);
      return res.data;
    } catch (error) {
      return rejectWithValue(getErrorMessage(error, '验证码发送失败'));
    }
  },
);

/** 找回密码（成功后清本地登录态，与后端作废会话对齐） */
export const resetPasswordAction = createAsyncThunk(
  'auth/resetPassword',
  async (params: IResetPasswordParams, { rejectWithValue, dispatch }) => {
    try {
      await resetPasswordByEmail(params);
      dispatch(logout());
      return true;
    } catch (error) {
      return rejectWithValue(getErrorMessage(error, '密码重置失败'));
    }
  },
);

/** 主动登出：调后端作废 token 并清本地 */
export const logoutAction = createAsyncThunk(
  'auth/logoutApi',
  async (_, { dispatch }) => {
    try {
      await logoutApi();
    } catch {
      // 网络失败也清本地，避免脏会话
    }
    dispatch(logout());
    dispatch(clearPostInteractions());
  },
);

/** 同步 /user/me（登录后、审核结果 SSE、进入个人页） */
export const fetchCurrentUserAction = createAsyncThunk(
  'auth/fetchCurrentUser',
  async (_, { rejectWithValue }) => {
    try {
      return await getCurrentUserApi();
    } catch (error) {
      return rejectWithValue(getErrorMessage(error, '获取资料失败'));
    }
  },
);

interface IAuthState {
  accessToken: string;
  user: IUserInfo | null;
  loading: boolean;
  error: string | null;
  successRedirecting: boolean;
}

function loadInitialUser(): IUserInfo | null {
  const raw = getUserInfo();
  if (!raw) return null;
  return normalizeUserInfo(raw as IUserInfo & { accountId: number });
}

const initialState: IAuthState = {
  accessToken: getAccessToken(),
  user: loadInitialUser(),
  loading: false,
  error: null,
  successRedirecting: false,
};

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    clearAuthError(state) {
      state.error = null;
    },
    logout(state) {
      resetAuthRefreshState();
      clearAuth();
      state.accessToken = '';
      state.user = null;
      state.error = null;
      state.successRedirecting = false;
    },
    setSuccessRedirecting(state, action: { payload: boolean }) {
      state.successRedirecting = action.payload;
    },
    /** 本地合并资料（提交审核后立刻回填 pending / 审核通过写回） */
    updateUserProfile(state, action: { payload: Partial<IUserInfo> }) {
      if (!state.user) return;
      const next = normalizeUserInfo(
        { ...state.user, ...action.payload, accountId: state.user.accountId },
        state.user,
      );
      state.user = next;
      setUserInfo(next);
    },
  },
  extraReducers: (builder) => {
    const handlePending = (state: IAuthState) => {
      state.loading = true;
      state.error = null;
    };
    const handleAuthFulfilled = (
      state: IAuthState,
      action: { payload: IAuthResult },
    ) => {
      const prev = state.user;
      const sameUser = prev?.accountId === action.payload.user.accountId;
      const user = persistAuthResult({
        ...action.payload,
        user: normalizeUserInfo(action.payload.user, sameUser ? prev : null),
      });
      state.loading = false;
      state.accessToken = action.payload.accessToken;
      state.user = user;
      state.successRedirecting = true;
    };
    const handleRejected = (
      state: IAuthState,
      action: { payload: unknown },
    ) => {
      state.loading = false;
      state.error = (action.payload as string) || '操作失败';
    };

    builder
      .addCase(loginAction.pending, (state) => {
        handlePending(state);
        resetAuthRefreshState();
        removeAccessToken();
        removeAccessExpireAt();
        state.accessToken = '';
      })
      .addCase(loginAction.fulfilled, handleAuthFulfilled)
      .addCase(loginAction.rejected, handleRejected)
      .addCase(registerAction.pending, handlePending)
      .addCase(registerAction.fulfilled, handleAuthFulfilled)
      .addCase(registerAction.rejected, handleRejected)
      .addCase(resetPasswordAction.pending, handlePending)
      .addCase(resetPasswordAction.fulfilled, (state) => {
        state.loading = false;
        state.error = null;
      })
      .addCase(resetPasswordAction.rejected, handleRejected)
      .addCase(fetchCurrentUserAction.fulfilled, (state, action) => {
        state.user = action.payload;
        setUserInfo(action.payload);
      });
  },
});

export const {
  clearAuthError,
  logout,
  setSuccessRedirecting,
  updateUserProfile,
} = authSlice.actions;
export default authSlice.reducer;
