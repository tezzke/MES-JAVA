import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { ElMessage } from 'element-plus';

type UnauthorizedHandler = () => void;
let unauthorizedHandler: UnauthorizedHandler | undefined;
let csrfToken = '';
let csrfPromise: Promise<string> | null = null;

export const http = axios.create({
  baseURL: '/',
  timeout: 15000,
  withCredentials: true,
});

export function onUnauthorized(handler: UnauthorizedHandler) {
  unauthorizedHandler = handler;
}

export function clearCsrfToken() {
  csrfToken = '';
  csrfPromise = null;
}

async function fetchCsrfToken(): Promise<string> {
  if (csrfToken) return csrfToken;
  if (!csrfPromise) {
    csrfPromise = axios
      .get<{ token: string }>('/api/auth/csrf', { withCredentials: true, timeout: 15000 })
      .then(({ data }) => {
        csrfToken = data.token;
        return csrfToken;
      })
      .finally(() => {
        csrfPromise = null;
      });
  }
  return csrfPromise;
}

http.interceptors.request.use(async (config: InternalAxiosRequestConfig) => {
  const method = config.method?.toUpperCase() ?? 'GET';
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    config.headers.set('X-XSRF-TOKEN', await fetchCsrfToken());
  }
  return config;
});

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<{ message?: string }>) => {
    if (error.response?.status === 401) {
      clearCsrfToken();
      unauthorizedHandler?.();
    } else if (error.response?.status === 403) {
      const message = typeof error.response.data === 'object'
        ? error.response.data?.message
        : undefined;
      ElMessage.warning(message ?? '请求被拒绝：权限不足、跨域来源未放行或 CSRF 校验失败');
    }
    return Promise.reject(error);
  },
);
