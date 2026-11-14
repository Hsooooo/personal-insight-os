import { lazy, Suspense, useEffect, useState } from 'react';
import { Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { toast } from 'sonner';
import { Loader2 } from 'lucide-react';
import { useAuthStore } from '@/stores/authStore';
import { api, isApiError, setUnauthorizedHandler } from '@/lib/api';
import { getRedirectTarget } from '@/lib/authRedirect';
import { Layout } from '@/components/layout/Layout';
import Login from '@/pages/Login';

const Dashboard = lazy(() => import('@/pages/Dashboard'));
const DataSources = lazy(() => import('@/pages/DataSources'));
const Activities = lazy(() => import('@/pages/Activities'));
const Health = lazy(() => import('@/pages/Health'));
const Graph = lazy(() => import('@/pages/Graph'));
const Ask = lazy(() => import('@/pages/Ask'));
const Insights = lazy(() => import('@/pages/Insights'));
const Goals = lazy(() => import('@/pages/Goals'));
const Settings = lazy(() => import('@/pages/Settings'));
const McpSettings = lazy(() => import('@/pages/McpSettings'));
const Finance = lazy(() => import('@/pages/Finance'));
const OAuthConsent = lazy(() => import('@/pages/OAuthConsent'));

function FullScreenSpinner({ label }: { label: string }) {
  return (
    <div className="flex h-screen w-full items-center justify-center bg-background" role="status">
      <div className="flex flex-col items-center gap-3">
        <Loader2 className="h-8 w-8 animate-spin text-indigo-500" aria-hidden="true" />
        <p className="text-sm text-muted-foreground">{label}</p>
      </div>
    </div>
  );
}

function RequireAuth() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const location = useLocation();
  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }
  return <Layout />;
}

function LoginRoute() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const location = useLocation();
  if (isAuthenticated) return <Navigate to={getRedirectTarget(location.state)} replace />;
  return <Login />;
}

/** 앱 부팅 시 한 번만 세션을 확인한다 (라우트 이동마다 /me를 호출하지 않는다). */
function useBootSessionCheck() {
  const [isVerifying, setIsVerifying] = useState(() => useAuthStore.getState().isAuthenticated);

  useEffect(() => {
    // 비로그인 상태면 초기값이 이미 false
    if (!useAuthStore.getState().isAuthenticated) return;
    const controller = new AbortController();
    api.auth
      .me({ signal: controller.signal })
      .then((user) => useAuthStore.getState().setUser(user))
      .catch((err: unknown) => {
        // 401은 apiClient가 이미 로그아웃 처리. 네트워크 오류는 세션을 유지한다.
        if (isApiError(err) && err.code === 'ABORTED') return;
      })
      .finally(() => {
        if (!controller.signal.aborted) setIsVerifying(false);
      });
    return () => controller.abort();
  }, []);

  return isVerifying;
}

function App() {
  const logout = useAuthStore((s) => s.logout);
  const isVerifying = useBootSessionCheck();

  useEffect(
    () =>
      setUnauthorizedHandler(() => {
        const wasAuthenticated = useAuthStore.getState().isAuthenticated;
        logout();
        // 라우트 가드(<Navigate>)가 /login으로 이동시킨다 — 전체 새로고침 없음
        if (wasAuthenticated) {
          toast.error('세션이 만료되었습니다. 다시 로그인해 주세요.', { id: 'session-expired' });
        }
      }),
    [logout]
  );

  if (isVerifying) {
    return <FullScreenSpinner label="Verifying session..." />;
  }

  return (
    <Suspense fallback={<FullScreenSpinner label="Loading..." />}>
      <Routes>
        <Route path="/login" element={<LoginRoute />} />
        <Route path="/oauth/consent" element={<OAuthConsent />} />
        <Route element={<RequireAuth />}>
          <Route path="/" element={<Dashboard />} />
          <Route path="/data-sources" element={<DataSources />} />
          <Route path="/activities" element={<Activities />} />
          <Route path="/health" element={<Health />} />
          <Route path="/finance" element={<Finance />} />
          <Route path="/graph" element={<Graph />} />
          <Route path="/ask" element={<Ask />} />
          <Route path="/insights" element={<Insights />} />
          <Route path="/goals" element={<Goals />} />
          <Route path="/mcp-settings" element={<McpSettings />} />
          <Route path="/settings" element={<Settings />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  );
}

export default App;
