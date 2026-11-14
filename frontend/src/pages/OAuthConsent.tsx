import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useMutation, useQuery } from '@tanstack/react-query';
import { AlertTriangle, GitGraph, Loader2, ShieldCheck, ShieldAlert, Clock } from 'lucide-react';
import { api, getErrorMessage, isApiError, refreshSession } from '@/lib/api';
import { getCustomScheme, getRedirectHost, isAllowedRedirect, isExpired, isTrustedRedirectHost } from '@/lib/oauth';
import { redirectTo } from '@/lib/navigation';
import { useAuthStore } from '@/stores/authStore';
import { LoginForm } from '@/components/auth/LoginForm';
import { ScopeList } from '@/components/oauth/ScopeList';
import { QueryError } from '@/components/common/QueryError';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import type { OAuthAuthorizationRequest } from '@/types';

/** 만료/소멸된 요청으로 간주할 HTTP 상태 */
function isGoneError(err: unknown): boolean {
  return isApiError(err) && [400, 404, 409, 410].includes(err.status);
}

function Shell({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 px-4 py-10">
      <main className="w-full max-w-md">
        <div className="mb-6 flex items-center justify-center gap-2">
          <GitGraph className="h-7 w-7 text-indigo-500" aria-hidden="true" />
          <span className="text-xl font-bold tracking-tight">Personal Insight OS</span>
        </div>
        {children}
      </main>
    </div>
  );
}

function Notice({ title, message }: { title: string; message: string }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-base">
          <Clock className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
          {title}
        </CardTitle>
        <CardDescription>{message}</CardDescription>
      </CardHeader>
    </Card>
  );
}

const EXPIRED_TITLE = '요청이 만료되었거나 유효하지 않습니다';
const EXPIRED_MESSAGE = '연결하려던 앱으로 돌아가 다시 연결을 시도해 주세요.';

/** 만료 시각이 지나면 다시 렌더링되도록 현재 시각을 갱신한다 */
function useExpiry(expiresAt: string | undefined): boolean {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!expiresAt) return;
    const ts = Date.parse(expiresAt);
    if (!Number.isFinite(ts)) return;
    const delay = ts - Date.now();
    if (delay <= 0) return;
    // setTimeout 최대치(약 24.8일) 초과 방지
    const timer = setTimeout(() => setNow(Date.now()), Math.min(delay + 50, 2_147_000_000));
    return () => clearTimeout(timer);
  }, [expiresAt]);
  return expiresAt ? isExpired(expiresAt, now) : false;
}

function ConsentCard({ request }: { request: OAuthAuthorizationRequest }) {
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);
  const [redirecting, setRedirecting] = useState(false);
  const expired = useExpiry(request.expiresAt);

  const decision = useMutation({
    mutationFn: async (approve: boolean) => {
      const result = await api.oauth.decide(request.requestId, approve);
      if (!isAllowedRedirect(result.redirectUrl, request.redirectUri)) {
        throw new Error('허용되지 않는 리디렉션 주소입니다.');
      }
      return result;
    },
    onSuccess: ({ redirectUrl }) => {
      setRedirecting(true);
      redirectTo(redirectUrl);
    },
    // 오류는 카드 안에 표시한다 (전역 토스트 생략)
    onError: () => undefined,
  });

  if (expired || (decision.isError && isGoneError(decision.error))) {
    return <Notice title={EXPIRED_TITLE} message={EXPIRED_MESSAGE} />;
  }

  const host = request.redirectHost?.trim() || getRedirectHost(request.redirectUri);
  const trusted = isTrustedRedirectHost(host);
  const customScheme = getCustomScheme(request.redirectUri);
  const clientName = request.clientName?.trim() || request.clientId;
  const busy = decision.isPending || redirecting;

  const handleSwitchAccount = async () => {
    try {
      await api.auth.logout();
    } catch {
      // 서버 로그아웃 실패는 무시
    }
    logout();
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-lg">
          <ShieldCheck className="h-5 w-5 text-indigo-500" aria-hidden="true" />
          앱 연결 승인
        </CardTitle>
        <CardDescription>
          <strong className="text-foreground">{clientName}</strong> 앱이 내 PIOS 계정에 접근하려고 합니다.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <dl className="grid grid-cols-[auto,1fr] gap-x-3 gap-y-1.5 rounded-md bg-muted/50 p-3 text-sm">
          <dt className="text-muted-foreground">앱 이름</dt>
          <dd className="break-all font-medium">{clientName}</dd>
          <dt className="text-muted-foreground">Client ID</dt>
          <dd className="break-all font-mono text-xs leading-5">{request.clientId}</dd>
          <dt className="text-muted-foreground">돌아갈 주소</dt>
          <dd className="break-all font-mono text-xs leading-5">{host}</dd>
          {user && (
            <>
              <dt className="text-muted-foreground">로그인 계정</dt>
              <dd className="break-all">{user.email}</dd>
            </>
          )}
        </dl>

        {customScheme && (
          <p role="note" className="rounded-md border bg-muted/40 p-3 text-sm text-muted-foreground">
            승인/거부 후 데스크톱 앱({customScheme})으로 돌아갑니다. 브라우저가 앱 열기를 확인하면 허용하세요.
          </p>
        )}

        {!trusted && (
          <div
            role="alert"
            className="flex gap-2 rounded-md border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900"
          >
            <ShieldAlert className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
            <p>
              승인하면 인증 코드가 외부 주소 <strong className="break-all">{host}</strong>(으)로 전달됩니다. 직접 연결을
              시작한 앱이 맞는지, 신뢰할 수 있는 주소인지 확인한 뒤 승인하세요.
            </p>
          </div>
        )}

        <div className="space-y-2">
          <p className="text-sm font-medium">요청 권한</p>
          <ScopeList scopes={request.scopes} />
        </div>

        {decision.isError && (
          <p role="alert" className="flex items-center gap-1.5 text-sm text-destructive">
            <AlertTriangle className="h-4 w-4" aria-hidden="true" />
            {getErrorMessage(decision.error, '처리하지 못했습니다. 다시 시도해 주세요.')}
          </p>
        )}
        {redirecting && (
          <p role="status" className="flex items-center gap-1.5 text-sm text-muted-foreground">
            <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
            앱으로 돌아가는 중...
          </p>
        )}
      </CardContent>
      <CardFooter className="flex flex-col gap-3">
        <div className="flex w-full gap-2">
          <Button variant="outline" className="flex-1" disabled={busy} onClick={() => decision.mutate(false)}>
            거부
          </Button>
          <Button className="flex-1" disabled={busy} onClick={() => decision.mutate(true)}>
            {decision.isPending && decision.variables === true && (
              <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden="true" />
            )}
            승인
          </Button>
        </div>
        <button
          type="button"
          onClick={handleSwitchAccount}
          disabled={busy}
          className="rounded-sm text-xs text-muted-foreground underline-offset-4 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          다른 계정으로 로그인
        </button>
      </CardFooter>
    </Card>
  );
}

function ConsentLoader({ requestId }: { requestId: string }) {
  const query = useQuery({
    queryKey: ['oauthRequest', requestId],
    queryFn: ({ signal }) => api.oauth.getRequest(requestId, { signal }),
    retry: false,
    staleTime: Infinity,
    gcTime: 0,
  });

  if (query.isPending) {
    return (
      <Card aria-busy="true" aria-label="요청 정보를 불러오는 중">
        <CardContent className="space-y-3 pt-6">
          <Skeleton className="h-6 w-40" />
          <Skeleton className="h-20 w-full" />
          <Skeleton className="h-16 w-full" />
          <Skeleton className="h-10 w-full" />
        </CardContent>
      </Card>
    );
  }
  if (query.isError) {
    if (isGoneError(query.error)) return <Notice title={EXPIRED_TITLE} message={EXPIRED_MESSAGE} />;
    return <QueryError error={query.error} onRetry={() => void query.refetch()} title="연결 요청을 불러오지 못했습니다" />;
  }
  return <ConsentCard request={query.data} />;
}

export default function OAuthConsent() {
  const [params] = useSearchParams();
  const requestId = params.get('request_id');
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const [checkingSession, setCheckingSession] = useState(() => !useAuthStore.getState().isAuthenticated);

  // 로그인 상태가 아니면 httpOnly refresh 쿠키로 세션 복구를 먼저 시도한다
  useEffect(() => {
    // 이미 로그인 상태면 초기값이 false라 확인할 필요 없음
    if (useAuthStore.getState().isAuthenticated) return;
    let cancelled = false;
    void refreshSession().finally(() => {
      if (!cancelled) setCheckingSession(false);
    });
    return () => {
      cancelled = true;
    };
  }, []);

  let content: React.ReactNode;
  if (!requestId) {
    content = <Notice title={EXPIRED_TITLE} message="요청 ID가 없습니다. 연결하려던 앱에서 다시 시도해 주세요." />;
  } else if (checkingSession && !isAuthenticated) {
    content = (
      <div className="flex justify-center py-10" role="status">
        <Loader2 className="h-6 w-6 animate-spin text-indigo-500" aria-hidden="true" />
        <span className="sr-only">세션 확인 중</span>
      </div>
    );
  } else if (!isAuthenticated) {
    content = <LoginForm description="앱 연결을 승인하려면 먼저 로그인하세요" />;
  } else {
    content = <ConsentLoader requestId={requestId} />;
  }

  return <Shell>{content}</Shell>;
}
