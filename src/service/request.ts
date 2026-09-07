import axios, {
  AxiosInstance,
  AxiosRequestConfig,
  AxiosResponse,
  InternalAxiosRequestConfig,
} from 'axios';
import { message } from 'antd';
import {
  BASE_URL,
  TIMEOUT,
  ACCESS_REFRESH_BUFFER_MS,
  CODE_ACCESS_EXPIRED,
  CODE_REFRESH_EXPIRED,
  CODE_TOO_MANY_REQUESTS,
} from './config';
import type { IDataType, IRefreshResult } from './types';
import {
  getAccessToken,
  setAccessAuth,
  clearAuth,
  isAccessTokenExpired,
  hasAuthSession,
} from '@/utils/storage';
import { emitAuthRequired } from '@/utils/authEvents';

// 自定义拦截器配置（实例创建时可覆盖默认行为）
export interface IHYInterceptors {
  requestInterceptor?: (
    config: InternalAxiosRequestConfig,
  ) => InternalAxiosRequestConfig | Promise<InternalAxiosRequestConfig>;
  requestInterceptorCatch?: (error: unknown) => unknown;
  responseInterceptor?: (
    res: AxiosResponse,
  ) => AxiosResponse | Promise<AxiosResponse>;
  responseInterceptorCatch?: (error: unknown) => unknown;
}

export interface IHYRequestConfig extends AxiosRequestConfig {
  interceptors?: IHYInterceptors;
  /** 登录/注册/刷新等接口不带 token、不走刷新逻辑 */
  skipAuth?: boolean;
  /** 内部标记：已重试过，避免死循环 */
  _retry?: boolean;
}

type AuthRequestConfig = InternalAxiosRequestConfig & {
  skipAuth?: boolean;
  _retry?: boolean;
};

/** 刷新单飞：同一标签页内的并发请求共享同一个 refresh Promise。 */
let refreshPromise: Promise<string> | null = null;
let authGeneration = 0;

class RefreshInvalidatedError extends Error {
  constructor() {
    super('refresh 已被新的登录态取消');
  }
}

/** 登录/登出后重置刷新单飞，避免旧 refresh 竞态拖死新会话 */
export function resetAuthRefreshState() {
  refreshPromise = null;
  authGeneration += 1;
}

function forceReLogin(tip = '登录已过期，请重新登录') {
  resetAuthRefreshState();
  clearAuth();
  emitAuthRequired(tip);
  message.error(tip);
}

/**
 * 刷新 access：不读、不传 refreshToken
 * 依赖 withCredentials 自动带上 HttpOnly Cookie
 */
async function doRefreshToken(retryCount = 0): Promise<IRefreshResult> {
  if (!hasAuthSession()) {
    throw new Error('无登录会话');
  }

  const res = await axios.post<IDataType<IRefreshResult>>(
    `${BASE_URL}/user/auth/refresh`,
    {},
    {
      timeout: TIMEOUT,
      withCredentials: true,
    },
  );
  const payload = res.data;
  if (payload.code === CODE_TOO_MANY_REQUESTS && retryCount < 3) {
    // 后端用会话锁串行化 refresh；跨标签页同时刷新时短暂返回 429，等待其他标签页完成轮换。
    await new Promise<void>((resolve) => {
      window.setTimeout(resolve, 200 * (retryCount + 1));
    });
    return doRefreshToken(retryCount + 1);
  }
  if (payload.code !== 200 || !payload.data) {
    throw Object.assign(new Error(payload.message || '刷新失败'), payload);
  }

  const data = payload.data;
  return data;
}

function refreshAccessTokenSingleFlight(): Promise<string> {
  if (refreshPromise) return refreshPromise;

  const generation = authGeneration;
  const nextPromise = doRefreshToken()
    .then((data) => {
      if (generation !== authGeneration) {
        throw new RefreshInvalidatedError();
      }
      setAccessAuth({
        accessToken: data.accessToken,
        accessExpiresIn: data.accessExpiresIn,
      });
      return data.accessToken;
    })
    .catch((error) => {
      if (!(error instanceof RefreshInvalidatedError)) {
        forceReLogin('登录已过期，请重新登录');
      }
      throw error;
    });
  refreshPromise = nextPromise;

  // 只清理当前这一轮，避免旧 refresh 的 finally 把新一轮误清掉。
  void nextPromise.then(
    () => {
      if (refreshPromise === nextPromise) refreshPromise = null;
    },
    () => {
      if (refreshPromise === nextPromise) refreshPromise = null;
    },
  );

  return nextPromise;
}

/**
 * SSE 不能使用 axios 的请求拦截器，因此在创建 EventSource 前复用同一套
 * access 刷新逻辑，避免 SSE 抢在普通请求刷新完成前携带旧 token 建连。
 */
export function refreshAccessTokenForSse(): Promise<string> {
  return refreshAccessTokenSingleFlight();
}

function getErrorResponse(error: unknown): {
  status?: number;
  data?: Partial<IDataType>;
  config?: AuthRequestConfig;
} | null {
  if (!error || typeof error !== 'object' || !('response' in error)) {
    return null;
  }
  const response = error.response;
  if (!response || typeof response !== 'object') return null;

  const responseData = 'data' in response ? response.data : undefined;
  return {
    status: 'status' in response ? Number(response.status) : undefined,
    data:
      responseData && typeof responseData === 'object'
        ? (responseData as Partial<IDataType>)
        : undefined,
    config:
      'config' in response ? (response.config as AuthRequestConfig) : undefined,
  };
}

async function retryAfterAccessExpired(
  config: AuthRequestConfig,
): Promise<AxiosResponse> {
  config._retry = true;
  const newToken = await refreshAccessTokenSingleFlight();
  config.headers.Authorization = `Bearer ${newToken}`;
  (
    window as Window & { __MOCK_ACCESS_TOKEN__?: string }
  ).__MOCK_ACCESS_TOKEN__ = newToken;
  return hyRequest.instance.request(config);
}

/**
 * HYRequest — axios 二次封装
 * 统一处理：baseURL、timeout、accessToken 注入、过期刷新、响应解包
 */
class HYRequest {
  instance: AxiosInstance;

  constructor(config: IHYRequestConfig) {
    this.instance = axios.create(config);

    this.instance.interceptors.request.use(
      config.interceptors?.requestInterceptor,
      config.interceptors?.requestInterceptorCatch,
    );

    this.instance.interceptors.response.use(
      config.interceptors?.responseInterceptor,
      config.interceptors?.responseInterceptorCatch,
    );
  }

  request<T = IDataType>(config: IHYRequestConfig): Promise<T> {
    return this.instance
      .request<IDataType, AxiosResponse<IDataType>>(config)
      .then((res) => res.data as T);
  }

  get<T = IDataType>(config: IHYRequestConfig): Promise<T> {
    return this.request<T>({ ...config, method: 'GET' });
  }

  post<T = IDataType>(config: IHYRequestConfig): Promise<T> {
    return this.request<T>({ ...config, method: 'POST' });
  }

  put<T = IDataType>(config: IHYRequestConfig): Promise<T> {
    return this.request<T>({ ...config, method: 'PUT' });
  }

  delete<T = IDataType>(config: IHYRequestConfig): Promise<T> {
    return this.request<T>({ ...config, method: 'DELETE' });
  }
}

const hyRequest: HYRequest = new HYRequest({
  baseURL: BASE_URL,
  timeout: TIMEOUT,
  withCredentials: true,
  interceptors: {
    requestInterceptor: async (config) => {
      const authConfig = config as AuthRequestConfig;
      if (authConfig.skipAuth) {
        // 登录/注册等公开接口不带旧 token，避免网关按失效 JWT 拦截
        delete authConfig.headers.Authorization;
        return config;
      }

      // access 缺失或临近过期都先静默刷新，避免“会话还在但本地 access 被清掉”时发出裸请求。
      if (hasAuthSession() && isAccessTokenExpired(ACCESS_REFRESH_BUFFER_MS)) {
        try {
          const newToken = await refreshAccessTokenSingleFlight();
          authConfig.headers.Authorization = `Bearer ${newToken}`;
          (
            window as Window & { __MOCK_ACCESS_TOKEN__?: string }
          ).__MOCK_ACCESS_TOKEN__ = newToken;
          return config;
        } catch (error) {
          return Promise.reject(error);
        }
      }

      const accessToken = getAccessToken();
      if (accessToken) {
        authConfig.headers.Authorization = `Bearer ${accessToken}`;
      }
      (
        window as Window & { __MOCK_ACCESS_TOKEN__?: string }
      ).__MOCK_ACCESS_TOKEN__ = accessToken;

      return config;
    },
    requestInterceptorCatch: (error) => Promise.reject(error),

    responseInterceptor: async (res) => {
      const data = res.data as IDataType;
      const config = res.config as AuthRequestConfig;

      if (data.code === 200) {
        return res;
      }

      // access 失效：刷新后重试原请求（仅一次）
      if (
        data.code === CODE_ACCESS_EXPIRED &&
        !config.skipAuth &&
        !config._retry
      ) {
        return retryAfterAccessExpired(config);
      }

      // refresh 失效或其它鉴权失败
      if (
        data.code === CODE_REFRESH_EXPIRED ||
        data.code === CODE_ACCESS_EXPIRED
      ) {
        forceReLogin(data.message || '登录已过期，请重新登录');
      }

      return Promise.reject(data) as Promise<AxiosResponse>;
    },
    responseInterceptorCatch: (error) => {
      const response = getErrorResponse(error);
      const data = response?.data;
      const config = response?.config;

      // 网关对失效 JWT 返回真实 HTTP 401，而不是 HTTP 200 + 业务码。
      // 这条路径也必须走与业务码 40101 相同的 refresh + 原请求重试。
      if (
        response?.status === 401 &&
        config &&
        !config.skipAuth &&
        !config._retry &&
        data?.code !== CODE_REFRESH_EXPIRED
      ) {
        return retryAfterAccessExpired(config);
      }

      if (data?.code === CODE_REFRESH_EXPIRED) {
        forceReLogin(data.message || '登录已过期，请重新登录');
      } else if (data?.code === CODE_ACCESS_EXPIRED && config?._retry) {
        forceReLogin(data.message || '登录已过期，请重新登录');
      }

      if (data && typeof data === 'object' && 'message' in data) {
        return Promise.reject(data);
      }
      // 保留开发环境网络诊断；生产构建不应因调试输出触发 no-console 检查。
      // eslint-disable-next-line no-console
      console.error('[Network Error]', error);
      return Promise.reject(error);
    },
  },
});

export default hyRequest;
