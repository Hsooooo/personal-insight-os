import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { useAuthStore } from '@/stores/authStore';
import { __resetApiClientForTests } from '@/lib/apiClient';
import OAuthConsent from './OAuthConsent';

vi.mock('@/lib/navigation', () => ({ redirectTo: vi.fn() }));
import { redirectTo } from '@/lib/navigation';

const USER = { id: 1, email: 'me@pios.test', displayName: 'Me', createdAt: '2026-01-01' };

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}
const ok = (data: unknown) => json(200, { success: true, message: null, data });

const REQUEST = {
  requestId: 'req-1',
  clientName: 'Claude Code',
  clientId: 'client-abc',
  redirectUri: 'https://evil.example.net/callback',
  redirectHost: 'evil.example.net',
  scopes: ['mcp:read', 'mcp:write'],
  expiresAt: new Date(Date.now() + 10 * 60_000).toISOString(),
};

type Route_ = (url: string, init: RequestInit | undefined) => Response | Promise<Response>;
let routeFetch: Route_;
const fetchMock = vi.fn<typeof fetch>((input, init) => {
  const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
  return Promise.resolve(routeFetch(url, init));
});

function renderConsent(search = '?request_id=req-1') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[`/oauth/consent${search}`]}>
        <Routes>
          <Route path="/oauth/consent" element={<OAuthConsent />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

beforeEach(() => {
  __resetApiClientForTests();
  vi.stubGlobal('fetch', fetchMock);
  fetchMock.mockClear();
  vi.mocked(redirectTo).mockClear();
});

afterEach(() => {
  vi.unstubAllGlobals();
  useAuthStore.setState({ token: null, user: null, isAuthenticated: false });
});

describe('OAuthConsent', () => {
  it('logged out: tries refresh cookie first, then shows the login form on the same URL', async () => {
    useAuthStore.setState({ token: null, user: null, isAuthenticated: false });
    routeFetch = (url) => (url.includes('/api/auth/refresh') ? json(401, {}) : json(500, {}));
    renderConsent();

    expect(await screen.findByRole('button', { name: 'Sign In' })).toBeInTheDocument();
    expect(fetchMock.mock.calls.some(([u]) => String(u).includes('/api/auth/refresh'))).toBe(true);
  });

  it('logged out with a valid refresh cookie: goes straight to the consent screen', async () => {
    useAuthStore.setState({ token: null, user: null, isAuthenticated: false });
    routeFetch = (url) => {
      if (url.includes('/api/auth/refresh')) return ok({ token: 't', user: USER });
      if (url.includes('/api/oauth/requests/req-1')) return ok(REQUEST);
      return json(404, {});
    };
    renderConsent();
    expect(await screen.findByText('앱 연결 승인')).toBeInTheDocument();
  });

  it('logs in inline and then loads the request', async () => {
    useAuthStore.setState({ token: null, user: null, isAuthenticated: false });
    routeFetch = (url) => {
      if (url.includes('/api/auth/refresh')) return json(401, {});
      if (url.includes('/api/auth/login')) return ok({ token: 't', user: USER });
      if (url.includes('/api/oauth/requests/req-1')) return ok(REQUEST);
      return json(404, {});
    };
    const user = userEvent.setup();
    renderConsent();
    await user.type(await screen.findByLabelText('Email'), 'me@pios.test');
    await user.type(screen.getByLabelText('Password'), 'pw');
    await user.click(screen.getByRole('button', { name: 'Sign In' }));
    expect(await screen.findByText('앱 연결 승인')).toBeInTheDocument();
  });

  describe('logged in', () => {
    beforeEach(() => {
      useAuthStore.setState({ token: 'tok', user: USER, isAuthenticated: true });
    });

    it('shows client, host, Korean scope labels and an external-host warning', async () => {
      routeFetch = () => ok(REQUEST);
      renderConsent();

      expect(await screen.findAllByText('Claude Code')).not.toHaveLength(0);
      expect(screen.getByText('client-abc')).toBeInTheDocument();
      expect(screen.getAllByText('evil.example.net').length).toBeGreaterThan(0);
      expect(screen.getByText(/데이터 읽기/)).toBeInTheDocument();
      expect(screen.getByText(/목표 수정·동기화·브리핑 생성 등/)).toBeInTheDocument();
      expect(screen.getByRole('alert')).toHaveTextContent('외부 주소');
    });

    it('does not warn for loopback redirect hosts', async () => {
      routeFetch = () => ok({ ...REQUEST, redirectUri: 'http://127.0.0.1:33418/cb', redirectHost: '127.0.0.1:33418' });
      renderConsent();
      await screen.findByText('127.0.0.1:33418');
      expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    });

    it('approve → POST decision {approve:true} → full navigation to redirectUrl', async () => {
      const redirectUrl = 'https://evil.example.net/callback?code=abc&state=xyz&iss=https%3A%2F%2Fpios';
      routeFetch = (url) => (url.endsWith('/decision') ? ok({ redirectUrl }) : ok(REQUEST));
      const user = userEvent.setup();
      renderConsent();

      await user.click(await screen.findByRole('button', { name: '승인' }));

      await waitFor(() => expect(redirectTo).toHaveBeenCalledWith(redirectUrl));
      const decisionCall = fetchMock.mock.calls.find(([u]) => String(u).endsWith('/api/oauth/requests/req-1/decision'));
      expect(decisionCall?.[1]?.method).toBe('POST');
      expect(JSON.parse(String(decisionCall?.[1]?.body))).toEqual({ approve: true });
      expect(screen.getByRole('button', { name: '승인' })).toBeDisabled();
    });

    it('deny → POST decision {approve:false} → navigates to the error redirect', async () => {
      const redirectUrl = 'https://evil.example.net/callback?error=access_denied&state=xyz';
      routeFetch = (url) => (url.endsWith('/decision') ? ok({ redirectUrl }) : ok(REQUEST));
      const user = userEvent.setup();
      renderConsent();

      await user.click(await screen.findByRole('button', { name: '거부' }));

      await waitFor(() => expect(redirectTo).toHaveBeenCalledWith(redirectUrl));
      const decisionCall = fetchMock.mock.calls.find(([u]) => String(u).endsWith('/decision'));
      expect(JSON.parse(String(decisionCall?.[1]?.body))).toEqual({ approve: false });
    });

    it('cursor:// private-use scheme: shows desktop-app note and follows the redirect', async () => {
      const cursorUri = 'cursor://anysphere.cursor-retrieval/oauth/user-pios-coach/callback';
      const redirectUrl = `${cursorUri}?code=abc&state=xyz&iss=https%3A%2F%2Fpios`;
      routeFetch = (url) =>
        url.endsWith('/decision')
          ? ok({ redirectUrl })
          : ok({ ...REQUEST, clientName: 'Cursor', redirectUri: cursorUri, redirectHost: 'cursor://anysphere.cursor-retrieval' });
      const user = userEvent.setup();
      renderConsent();

      expect(await screen.findByText(/데스크톱 앱\(cursor\)으로 돌아갑니다/)).toBeInTheDocument();
      expect(screen.getByRole('alert')).toHaveTextContent('외부 주소'); // non-local warning kept
      await user.click(screen.getByRole('button', { name: '승인' }));
      await waitFor(() => expect(redirectTo).toHaveBeenCalledWith(redirectUrl));
    });

    it('rejects a javascript: redirect URL', async () => {
      routeFetch = (url) => (url.endsWith('/decision') ? ok({ redirectUrl: 'javascript:alert(1)' }) : ok(REQUEST));
      const user = userEvent.setup();
      renderConsent();
      await user.click(await screen.findByRole('button', { name: '승인' }));
      expect(await screen.findByText('허용되지 않는 리디렉션 주소입니다.')).toBeInTheDocument();
      expect(redirectTo).not.toHaveBeenCalled();
    });

    it('rejects a redirect URL that does not match the registered redirect_uri', async () => {
      routeFetch = (url) =>
        url.endsWith('/decision') ? ok({ redirectUrl: 'https://evil.example.net/callback-other?code=abc' }) : ok(REQUEST);
      const user = userEvent.setup();
      renderConsent();
      await user.click(await screen.findByRole('button', { name: '승인' }));
      expect(await screen.findByText('허용되지 않는 리디렉션 주소입니다.')).toBeInTheDocument();
      expect(redirectTo).not.toHaveBeenCalled();
    });

    it('expired / unknown request (404) → expired notice, no buttons', async () => {
      routeFetch = () => json(404, { success: false, message: 'not found', data: null });
      renderConsent();
      expect(await screen.findByText('요청이 만료되었거나 유효하지 않습니다')).toBeInTheDocument();
      expect(screen.queryByRole('button', { name: '승인' })).not.toBeInTheDocument();
    });

    it('request already past expiresAt → expired notice', async () => {
      routeFetch = () => ok({ ...REQUEST, expiresAt: new Date(Date.now() - 1000).toISOString() });
      renderConsent();
      expect(await screen.findByText('요청이 만료되었거나 유효하지 않습니다')).toBeInTheDocument();
    });

    it('decision on an expired request (410) → expired notice', async () => {
      routeFetch = (url) => (url.endsWith('/decision') ? json(410, { success: false, message: 'expired', data: null }) : ok(REQUEST));
      const user = userEvent.setup();
      renderConsent();
      await user.click(await screen.findByRole('button', { name: '승인' }));
      expect(await screen.findByText('요청이 만료되었거나 유효하지 않습니다')).toBeInTheDocument();
      expect(redirectTo).not.toHaveBeenCalled();
    });

    it('missing request_id → invalid notice', async () => {
      routeFetch = () => ok(REQUEST);
      renderConsent('');
      expect(await screen.findByText(/요청 ID가 없습니다/)).toBeInTheDocument();
    });
  });
});
