import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useAuthStore } from '@/stores/authStore';
import {
  __resetApiClientForTests,
  ApiError,
  fetchApi,
  parseRetryAfter,
  setUnauthorizedHandler,
} from './apiClient';

const USER = { id: 1, email: 'a@b.c', displayName: null, createdAt: '2026-01-01' };

function json(status: number, body: unknown, headers: Record<string, string> = {}) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json', ...headers } });
}

const ok = <T,>(data: T) => json(200, { success: true, message: null, data });

const fetchMock = vi.fn<typeof fetch>();

function urlOf(call: Parameters<typeof fetch>): string {
  const [input] = call;
  return typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
}

beforeEach(() => {
  __resetApiClientForTests();
  fetchMock.mockReset();
  vi.stubGlobal('fetch', fetchMock);
  useAuthStore.setState({ token: 'old-token', user: USER, isAuthenticated: true });
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('fetchApi', () => {
  it('unwraps ApiResponse.data and sends the bearer token', async () => {
    fetchMock.mockResolvedValueOnce(ok({ hello: 'world' }));
    await expect(fetchApi('/api/x')).resolves.toEqual({ hello: 'world' });
    const init = fetchMock.mock.calls[0][1];
    expect((init?.headers as Record<string, string>).Authorization).toBe('Bearer old-token');
    expect(init?.credentials).toBe('include');
  });

  it('returns undefined for empty 2xx bodies (e.g. 204)', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));
    await expect(fetchApi('/api/x', { method: 'DELETE' })).resolves.toBeUndefined();
  });

  it('network failure → NETWORK_ERROR and does NOT log out', async () => {
    const handler = vi.fn();
    setUnauthorizedHandler(handler);
    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'));
    const err = await fetchApi('/api/x').catch((e: unknown) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err).toMatchObject({ code: 'NETWORK_ERROR', status: 0 });
    expect(handler).not.toHaveBeenCalled();
    expect(useAuthStore.getState().isAuthenticated).toBe(true);
  });

  it('non-JSON 502 → readable INVALID_RESPONSE error', async () => {
    fetchMock.mockResolvedValueOnce(new Response('<html>Bad Gateway</html>', { status: 502 }));
    await expect(fetchApi('/api/x')).rejects.toMatchObject({
      code: 'INVALID_RESPONSE',
      status: 502,
      message: expect.stringContaining('502'),
    });
  });

  it('HTTP error surfaces the server message', async () => {
    fetchMock.mockResolvedValueOnce(json(400, { success: false, message: '잘못된 요청', data: null }));
    await expect(fetchApi('/api/x')).rejects.toMatchObject({ code: 'HTTP_ERROR', status: 400, message: '잘못된 요청' });
  });

  it('2xx with success=false → API_ERROR', async () => {
    fetchMock.mockResolvedValueOnce(json(200, { success: false, message: '실패', data: null }));
    await expect(fetchApi('/api/x')).rejects.toMatchObject({ code: 'API_ERROR', message: '실패' });
  });

  it('passes AbortSignal through and maps aborts to ABORTED', async () => {
    const controller = new AbortController();
    fetchMock.mockImplementationOnce((_input, init) => {
      expect(init?.signal).toBe(controller.signal);
      return Promise.reject(new DOMException('aborted', 'AbortError'));
    });
    controller.abort();
    await expect(fetchApi('/api/x', { signal: controller.signal })).rejects.toMatchObject({ code: 'ABORTED' });
  });

  it('429 → RATE_LIMITED with Retry-After', async () => {
    fetchMock.mockResolvedValueOnce(json(429, { success: false, message: 'Too many', data: null }, { 'Retry-After': '120' }));
    await expect(fetchApi('/api/auth/login', { method: 'POST', skipAuthRefresh: true })).rejects.toMatchObject({
      code: 'RATE_LIMITED',
      status: 429,
      retryAfterSeconds: 120,
      message: expect.stringContaining('2분 후'),
    });
  });
});

describe('401 handling', () => {
  it('refreshes once and retries with the new token', async () => {
    fetchMock
      .mockResolvedValueOnce(json(401, { success: false, message: 'expired', data: null }))
      .mockResolvedValueOnce(ok({ token: 'new-token', user: USER }))
      .mockResolvedValueOnce(ok('done'));

    await expect(fetchApi('/api/x')).resolves.toBe('done');
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(urlOf(fetchMock.mock.calls[1])).toContain('/api/auth/refresh');
    const retryHeaders = fetchMock.mock.calls[2][1]?.headers as Record<string, string>;
    expect(retryHeaders.Authorization).toBe('Bearer new-token');
    expect(useAuthStore.getState().token).toBe('new-token');
  });

  it('retries at most once per request (second 401 → UNAUTHORIZED)', async () => {
    const handler = vi.fn();
    setUnauthorizedHandler(handler);
    fetchMock
      .mockResolvedValueOnce(json(401, {}))
      .mockResolvedValueOnce(ok({ token: 'new-token', user: USER }))
      .mockResolvedValueOnce(json(401, {}));

    await expect(fetchApi('/api/x')).rejects.toMatchObject({ code: 'UNAUTHORIZED', status: 401 });
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(handler).toHaveBeenCalledTimes(1);
  });

  it('single-flight: concurrent 401s share one refresh, and all waiters reject when it fails', async () => {
    const handler = vi.fn();
    setUnauthorizedHandler(handler);
    let resolveRefresh: (r: Response) => void = () => undefined;
    fetchMock.mockImplementation((input) => {
      const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
      if (url.includes('/api/auth/refresh')) {
        return new Promise<Response>((resolve) => {
          resolveRefresh = resolve;
        });
      }
      return Promise.resolve(json(401, {}));
    });

    const results = Promise.allSettled([fetchApi('/api/a'), fetchApi('/api/b'), fetchApi('/api/c')]);
    await vi.waitFor(() => expect(fetchMock.mock.calls.filter((c) => urlOf(c).includes('/refresh'))).toHaveLength(1));
    resolveRefresh(json(401, { success: false }));

    const settled = await results;
    expect(settled.every((r) => r.status === 'rejected')).toBe(true);
    for (const r of settled) {
      if (r.status === 'rejected') expect(r.reason).toMatchObject({ code: 'UNAUTHORIZED' });
    }
    expect(fetchMock.mock.calls.filter((c) => urlOf(c).includes('/refresh'))).toHaveLength(1);
  });

  it('refresh network failure → NETWORK_ERROR without logout', async () => {
    const handler = vi.fn();
    setUnauthorizedHandler(handler);
    fetchMock.mockResolvedValueOnce(json(401, {})).mockRejectedValueOnce(new TypeError('offline'));
    await expect(fetchApi('/api/x')).rejects.toMatchObject({ code: 'NETWORK_ERROR' });
    expect(handler).not.toHaveBeenCalled();
  });

  it('login 401 does not trigger refresh or logout and surfaces the server message', async () => {
    const handler = vi.fn();
    setUnauthorizedHandler(handler);
    fetchMock.mockResolvedValueOnce(json(401, { success: false, message: '이메일 또는 비밀번호가 올바르지 않습니다', data: null }));
    await expect(
      fetchApi('/api/auth/login', { method: 'POST', body: '{}', skipAuthRefresh: true })
    ).rejects.toMatchObject({ status: 401, code: 'HTTP_ERROR', message: '이메일 또는 비밀번호가 올바르지 않습니다' });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(handler).not.toHaveBeenCalled();
  });

  it('default unauthorized handler logs the user out', async () => {
    fetchMock.mockResolvedValueOnce(json(401, {})).mockResolvedValueOnce(json(401, {}));
    await expect(fetchApi('/api/x')).rejects.toMatchObject({ code: 'UNAUTHORIZED' });
    expect(useAuthStore.getState().isAuthenticated).toBe(false);
  });
});

describe('parseRetryAfter', () => {
  it('parses seconds and HTTP dates', () => {
    expect(parseRetryAfter('30')).toBe(30);
    expect(parseRetryAfter(null)).toBeUndefined();
    expect(parseRetryAfter('garbage')).toBeUndefined();
    const now = Date.parse('2026-11-14T00:00:00Z');
    expect(parseRetryAfter('Sat, 14 Nov 2026 00:01:00 GMT', now)).toBe(60);
  });
});
