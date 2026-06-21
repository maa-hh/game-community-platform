export type BlockType = "FLOW" | "DEGRADE";

export type ApiEnvelope<T> = {
  code: number;
  message: string;
  data: T;
};

export class ApiError extends Error {
  status: number;
  code: number;
  blockType?: BlockType;

  constructor(message: string, status: number, code = status, blockType?: BlockType) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.blockType = blockType;
  }
}

export const API_PREFIX = (import.meta.env.VITE_API_BASE_URL ?? "/api").replace(/\/$/, "");
const TOKEN_KEY = "game-community-token";

export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (token: string) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY)
};

type RequestOptions = {
  method?: "GET" | "POST" | "PUT" | "DELETE";
  body?: unknown;
  auth?: boolean;
  retryOnAuthFailure?: boolean;
};

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const payload = await requestEnvelope<T>(path, options);
  return payload.data;
}

export async function requestEnvelope<T>(path: string, options: RequestOptions = {}): Promise<ApiEnvelope<T>> {
  const response = await performRequest(path, options);
  const text = await response.text();
  const payload = text ? safeJson<ApiEnvelope<T>>(text) : null;

  if (!response.ok && response.status === 401 && options.auth !== false && options.retryOnAuthFailure !== false) {
    const refreshed = await tryRefreshAccessToken();
    if (refreshed) {
      return requestEnvelope<T>(path, { ...options, retryOnAuthFailure: false });
    }
  }

  if (!response.ok) {
    const data = payload?.data as { blockType?: BlockType } | null | undefined;
    throw new ApiError(payload?.message ?? response.statusText, response.status, payload?.code, data?.blockType);
  }

  if (!payload) {
    return { code: response.status, message: response.statusText, data: undefined as T };
  }
  if (payload.code !== 200) {
    const data = payload.data as { blockType?: BlockType } | null | undefined;
    throw new ApiError(payload.message, response.status, payload.code, data?.blockType);
  }
  return payload;
}

async function performRequest(path: string, options: RequestOptions): Promise<Response> {
  const headers = new Headers();
  const isFormData = options.body instanceof FormData;
  if (!isFormData) {
    headers.set("Content-Type", "application/json");
  }
  if (options.auth !== false) {
    const token = tokenStore.get();
    if (token) {
      headers.set("Authorization", `Bearer ${token}`);
    }
  }

  return fetch(`${API_PREFIX}${path}`, {
    method: options.method ?? "GET",
    headers,
    body: options.body == null ? undefined : isFormData ? (options.body as FormData) : JSON.stringify(options.body),
    credentials: "include"
  });
}

let refreshPromise: Promise<boolean> | null = null;

async function tryRefreshAccessToken(): Promise<boolean> {
  if (!refreshPromise) {
    refreshPromise = refreshAccessTokenRequest().finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
}

async function refreshAccessTokenRequest(): Promise<boolean> {
  const response = await fetch(`${API_PREFIX}/user/token/refresh`, {
    method: "POST",
    credentials: "include"
  });
  const text = await response.text();
  const payload = text ? safeJson<ApiEnvelope<{ accessToken: string }>>(text) : null;
  if (!response.ok || !payload || payload.code !== 200 || !payload.data?.accessToken) {
    tokenStore.clear();
    return false;
  }
  tokenStore.set(payload.data.accessToken);
  return true;
}

function safeJson<T>(text: string): T | null {
  try {
    return JSON.parse(text) as T;
  } catch {
    return null;
  }
}
