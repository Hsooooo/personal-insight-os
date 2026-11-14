import { Suspense } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { QueryErrorResetBoundary } from '@tanstack/react-query';
import { Sidebar } from './Sidebar';
import { Header } from './Header';
import { ErrorBoundary } from '@/components/common/ErrorBoundary';
import { PageSkeleton } from '@/components/common/PageSkeleton';

export function Layout() {
  const location = useLocation();
  return (
    <div className="flex min-h-screen bg-background">
      <a
        href="#main-content"
        className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-50 focus:rounded-md focus:bg-background focus:px-4 focus:py-2 focus:shadow"
      >
        본문으로 건너뛰기
      </a>
      <Sidebar />
      <div className="flex flex-1 flex-col md:pl-64">
        <Header />
        <main id="main-content" tabIndex={-1} className="flex-1 p-4 focus:outline-none md:p-8">
          <div className="mx-auto max-w-7xl">
            <QueryErrorResetBoundary>
              {({ reset }) => (
                <ErrorBoundary resetKey={location.pathname} onReset={reset}>
                  <Suspense fallback={<PageSkeleton />}>
                    <Outlet />
                  </Suspense>
                </ErrorBoundary>
              )}
            </QueryErrorResetBoundary>
          </div>
        </main>
      </div>
    </div>
  );
}
