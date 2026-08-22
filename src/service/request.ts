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
} from './config';
import type { IDataType, IRefreshResult } from './types';
import {
  getAccessToken,
  setAccessAuth,
  clearAuth,
  isAccessTokenExpired,
  hasAuthSession,
  setAuthTip,
} from '@/utils/storage';

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

/** 刷新单飞：并发 401 时只发一次 refresh，其余排队 */
let isRefreshing = false;
let refreshWaitQueue: Array<{
  resolve: (token: string) => void;
  reject: (error: unknown) => void;
}> = [];

function enqueueRefreshWaiters() {
  return new Promise<string>((resolve, reject) => {
    refreshWaitQueue.push({ resolve, reject });
  });
}

function resolveRefreshWaiters(token: string) {
  refreshWaitQueue.forEach((item) => item.resolve(token));
  refreshWaitQueue = [];
}

function rejectRefreshWaiters(error: unknown) {
  refreshWaitQueue.forEach((item) => item.reject(error));
  refreshWaitQueue = [];
}

/** 登录/登出后重置刷新单飞，避免旧 refresh 竞态拖死新会话 */
export function resetAuthRefreshState() {
  isRefreshing = false;
  refreshWaitQueue = [];
}

function forceReLogin(tip = '登录已过期，请重新登录') {
  resetAuthRefreshState();
  clearAuth();
  setAuthTip(tip);
  message.error(tip);
  if (!window.location.pathname.includes('/login')) {
    window.location.assign('/login');
  }
}

/**
 * 刷新 access：不读、不传 refreshToken
 * 依赖 withCredentials 自动带上 HttpOnly Cookie
 */
async function doRefreshToken(): Promise<string> {
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
  if (payload.code !== 200 || !payload.data) {
    throw Object.assign(new Error(payload.message || '刷新失败'), payload);
  }

  const data = payload.data;
  setAccessAuth({
    accessToken: data.accessToken,
    accessExpiresIn: data.accessExpiresIn,
  });
  return data.accessToken;
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

      // 有 access 且临近过期才静默刷新（仅有会话标记、尚无 token 时不刷新）
      if (
        hasAuthSession() &&
        getAccessToken() &&
        isAccessTokenExpired(ACCESS_REFRESH_BUFFER_MS)
      ) {
        if (!isRefreshing) {
          isRefreshing = true;
          try {
            const newToken = await doRefreshToken();
            resolveRefreshWaiters(newToken);
          } catch (error) {
            rejectRefreshWaiters(error);
            forceReLogin('登录已过期，请重新登录');
            return Promise.reject(error);
          } finally {
            isRefreshing = false;
          }
        } else {
          const newToken = await enqueueRefreshWaiters();
          authConfig.headers.Authorization = `Bearer ${newToken}`;
          (
            window as Window & { __MOCK_ACCESS_TOKEN__?: string }
          ).__MOCK_ACCESS_TOKEN__ = newToken;
          return config;
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
        config._retry = true;

        if (!isRefreshing) {
          isRefreshing = true;
          try {
            const newToken = await doRefreshToken();
            resolveRefreshWaiters(newToken);
            config.headers.Authorization = `Bearer ${newToken}`;
            (
              window as Window & { __MOCK_ACCESS_TOKEN__?: string }
            ).__MOCK_ACCESS_TOKEN__ = newToken;
            return hyRequest.instance.request(config);
          } catch (error) {
            rejectRefreshWaiters(error);
            forceReLogin('登录已过期，请重新登录');
            return Promise.reject(error);
          } finally {
            isRefreshing = false;
          }
        }

        const newToken = await enqueueRefreshWaiters();
        config.headers.Authorization = `Bearer ${newToken}`;
        (
          window as Window & { __MOCK_ACCESS_TOKEN__?: string }
        ).__MOCK_ACCESS_TOKEN__ = newToken;
        return hyRequest.instance.request(config);
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
      // 网关 HTTP 401 等场景：尽量解析 JSON body，避免只显示 "Request failed with status code 401"
      if (
        error &&
        typeof error === 'object' &&
        'response' in error &&
        error.response &&
        typeof error.response === 'object' &&
        'data' in error.response &&
        error.response.data &&
        typeof error.response.data === 'object' &&
        'message' in error.response.data
      ) {
        return Promise.reject(error.response.data);
      }
      // 保留开发环境网络诊断；生产构建不应因调试输出触发 no-console 检查。
      // eslint-disable-next-line no-console
      console.error('[Network Error]', error);
      return Promise.reject(error);
    },
  },
});

export default hyRequest;
