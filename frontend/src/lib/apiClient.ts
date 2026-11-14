import { useAuthStore } from '@/stores/authStore';
import type { ApiResponse, AuthResponse } from '@/types';

const API_BASE = import.meta.env.VITE_API_URL || '';

/**
 * 오류 분류 코드.
 * 참고: 로그인/회원가입/로그아웃은 skipAuthRefresh로 호출되므로 401이어도 refresh·로그아웃 흐름을 타지 않고
 *       서버 메시지(예: "이메일 또는 비밀번호가 올바르지 않습니다")가 그대로 HTTP_ERROR로 전달된다.
 * - NETWORK_ERROR: 서버에 도달하지 못함 (오프라인, DNS, CORS 등) — 로그아웃하지 않는다.
 * - ABORTED: AbortSignal로 요청이 취소됨 (TanStack Query 취소 등).
 * - UNAUTHORIZED: 401 + 토큰 재발급 실패 → 세션 만료.
 * - INVALID_RESPONSE: 응답 본문이 JSON이 아님 (예: 프록시 502 HTML).
 * - HTTP_ERROR: 4xx/5xx 응답.
 * - RATE_LIMITED: 429 (예: 로그인 5회 실패). retryAfterSeconds에 Retry-After 값.
 * - API_ERROR: 2xx지만 ApiResponse.success === false.
 */
export type ApiErrorCode =
  | 'NETWORK_ERROR'
  | 'ABORTED'
  | 'UNAUTHORIZED'
  | 'INVALID_RESPONSE'
  | 'HTTP_ERROR'
  | 'RATE_LIMITED'
  | 'API_ERROR';

export class ApiError extends Error {
  readonly status: number;
  readonly code: ApiErrorCode;
  /** 429 응답의 Retry-After (초) */
  readonly retryAfterSeconds?: number;

  constructor(message: string, status: number, code: ApiErrorCode, retryAfterSeconds?: number) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.retryAfterSeconds = retryAfterSeconds;
  }
}

export function isApiError(err: unknown): err is ApiError {
  return err instanceof ApiError;
}

/** 사용자에게 보여줄 오류 메시지 */
export function getErrorMessage(err: unknown, fallback = '요청을 처리하지 못했습니다.'): string {
  if (isApiError(err)) {
    if (err.code === 'NETWORK_ERROR') return '서버에 연결할 수 없습니다. 네트워크 상태를 확인해 주세요.';
    return err.message || fallback;
  }
  if (err instanceof Error && err.message) return err.message;
  return fallback;
}

/** Retry-After 헤더(초 또는 HTTP-date) → 초. 해석 불가 시 undefined */
export function parseRetryAfter(value: string | null, now: number = Date.now()): number | undefined {
  if (!value) return undefined;
  const trimmed = value.trim();
  if (/^\d+$/.test(trimmed)) return Number(trimmed);
  const date = Date.parse(trimmed);
  if (Number.isNaN(date)) return undefined;
  return Math.max(0, Math.ceil((date - now) / 1000));
}

function formatWait(seconds: number): string {
  if (seconds < 60) return `${seconds}초`;
  return `${Math.ceil(seconds / 60)}분`;
}

export function rateLimitMessage(retryAfterSeconds: number | undefined): string {
  return retryAfterSeconds && retryAfterSeconds > 0
    ? `요청이 너무 많습니다. ${formatWait(retryAfterSeconds)} 후에 다시 시도해 주세요.`
    : '요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.';
}

export interface RequestOptions {
  signal?: AbortSignal;
}

interface FetchOptions extends RequestInit {
  /** 401 시 refresh를 시도하지 않는다 (로그인/회원가입/refresh 자체). */
  skipAuthRefresh?: boolean;
}

type UnauthorizedHandler = () => void;

let unauthorizedHandler: UnauthorizedHandler = () => {
  useAuthStore.getState().logout();
};

/** 세션이 만료되었을 때 호출될 콜백 (앱이 라우터 기반 이동을 위해 등록한다). */
export function setUnauthorizedHandler(handler: UnauthorizedHandler): () => void {
  const previous = unauthorizedHandler;
  unauthorizedHandler = handler;
  return () => {
    unauthorizedHandler = previous;
  };
}

type RefreshResult = 'ok' | 'unauthorized' | 'network';

let refreshPromise: Promise<RefreshResult> | null = null;

async function doRefresh(): Promise<RefreshResult> {
  let res: Response;
  try {
    res = await fetch(`${API_BASE}/api/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
    });
  } catch {
    return 'network';
  }
  if (!res.ok) {
    // 5xx는 서버 장애이므로 세션 만료로 단정하지 않는다
    return res.status >= 500 ? 'network' : 'unauthorized';
  }
  try {
    const data = (await res.json()) as ApiResponse<AuthResponse>;
    if (!data.success || !data.data) return 'unauthorized';
    useAuthStore.getState().setAuth(data.data.token, data.data.user);
    return 'ok';
  } catch {
    return 'network';
  }
}

/**
 * httpOnly refresh 쿠키로 access 토큰을 재발급한다.
 * 동시에 여러 요청이 401을 받아도 refresh 요청은 한 번만 나간다 (single-flight).
 * 실패 시 대기 중인 모든 호출자가 같은 결과를 받으므로 영원히 대기하지 않는다.
 */
export function refreshSession(): Promise<RefreshResult> {
  if (!refreshPromise) {
    refreshPromise = doRefresh().finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
}

function isAbortError(err: unknown): boolean {
  return (
    (err instanceof DOMException && err.name === 'AbortError') ||
    (err instanceof Error && err.name === 'AbortError')
  );
}

async function parseBody(response: Response): Promise<{ ok: true; body: unknown } | { ok: false }> {
  let text: string;
  try {
    text = await response.text();
  } catch {
    return { ok: false };
  }
  if (!text) return { ok: true, body: null };
  try {
    return { ok: true, body: JSON.parse(text) as unknown };
  } catch {
    return { ok: false };
  }
}

function isApiResponse(body: unknown): body is ApiResponse<unknown> {
  return typeof body === 'object' && body !== null && 'success' in body;
}

function extractMessage(body: unknown): string | null {
  if (typeof body !== 'object' || body === null) return null;
  const record = body as Record<string, unknown>;
  const candidate = record.message ?? record.error_description ?? record.error;
  return typeof candidate === 'string' && candidate ? candidate : null;
}

export async function fetchApi<T>(path: string, options: FetchOptions = {}): Promise<T> {
  return request<T>(path, options, false);
}

async function request<T>(path: string, options: FetchOptions, isRetry: boolean): Promise<T> {
  const { skipAuthRefresh, ...init } = options;
  const token = useAuthStore.getState().token;
  const isFormData = typeof FormData !== 'undefined' && init.body instanceof FormData;

  const headers: Record<string, string> = {
    ...(!isFormData ? { 'Content-Type': 'application/json' } : {}),
    ...((init.headers as Record<string, string> | undefined) ?? {}),
  };
  if (token) headers['Authorization'] = `Bearer ${token}`;

  let response: Response;
  try {
    response = await fetch(`${API_BASE}${path}`, { ...init, headers, credentials: 'include' });
  } catch (err) {
    if (isAbortError(err) || init.signal?.aborted) {
      throw new ApiError('요청이 취소되었습니다.', 0, 'ABORTED');
    }
    throw new ApiError('서버에 연결할 수 없습니다.', 0, 'NETWORK_ERROR');
  }

  if (response.status === 401 && !skipAuthRefresh) {
    if (!isRetry) {
      const result = await refreshSession();
      if (result === 'ok') {
        return request<T>(path, options, true);
      }
      if (result === 'network') {
        throw new ApiError('서버에 연결할 수 없습니다.', 0, 'NETWORK_ERROR');
      }
    }
    unauthorizedHandler();
    throw new ApiError('세션이 만료되었습니다. 다시 로그인해 주세요.', 401, 'UNAUTHORIZED');
  }

  if (response.status === 429) {
    const retryAfter = parseRetryAfter(response.headers.get('Retry-After'));
    throw new ApiError(rateLimitMessage(retryAfter), 429, 'RATE_LIMITED', retryAfter);
  }

  const parsed = await parseBody(response);
  if (!parsed.ok) {
    if (init.signal?.aborted) throw new ApiError('요청이 취소되었습니다.', 0, 'ABORTED');
    throw new ApiError(
      `서버 응답을 해석할 수 없습니다 (HTTP ${response.status}).`,
      response.status,
      'INVALID_RESPONSE'
    );
  }

  const body = parsed.body;
  if (!response.ok) {
    throw new ApiError(extractMessage(body) ?? `요청에 실패했습니다 (HTTP ${response.status}).`, response.status, 'HTTP_ERROR');
  }

  if (body === null) return undefined as T;
  if (!isApiResponse(body)) {
    throw new ApiError('예상하지 못한 서버 응답 형식입니다.', response.status, 'INVALID_RESPONSE');
  }
  if (!body.success) {
    throw new ApiError(body.message || '요청에 실패했습니다.', response.status, 'API_ERROR');
  }
  return body.data as T;
}

/** 테스트 전용: 모듈 상태 초기화 */
export function __resetApiClientForTests() {
  refreshPromise = null;
  unauthorizedHandler = () => {
    useAuthStore.getState().logout();
  };
}
