import { useEffect, useState } from 'react';
import { Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { useAuthStore } from '@/stores/authStore';
import { api } from '@/lib/api';
import { Layout } from '@/components/layout/Layout';
import { Loader2 } from 'lucide-react';
import Login from '@/pages/Login';
import Dashboard from '@/pages/Dashboard';
import DataSources from '@/pages/DataSources';
import Activities from '@/pages/Activities';
import Health from '@/pages/Health';
import Graph from '@/pages/Graph';
import Ask from '@/pages/Ask';
import Insights from '@/pages/Insights';
import Goals from '@/pages/Goals';
import Settings from '@/pages/Settings';
import McpSettings from '@/pages/McpSettings';
import Finance from '@/pages/Finance';

function App() {
  const { isAuthenticated, logout } = useAuthStore();
  const location = useLocation();
  const [isVerifying, setIsVerifying] = useState(isAuthenticated);

  useEffect(() => {
    if (!isAuthenticated) {
      setIsVerifying(false);
      return;
    }

    let cancelled = false;

    api.auth
      .me()
      .then(() => {
        if (!cancelled) setIsVerifying(false);
      })
      .catch(() => {
        if (!cancelled) {
          logout();
          setIsVerifying(false);
          if (location.pathname !== '/login') {
            window.location.href = '/login';
          }
        }
      });

    return () => {
      cancelled = true;
    };
  }, [isAuthenticated, logout, location.pathname]);

  if (isVerifying) {
    return (
      <div className="flex h-screen w-full items-center justify-center bg-background">
        <div className="flex flex-col items-center gap-3">
          <Loader2 className="h-8 w-8 animate-spin text-indigo-500" />
          <p className="text-sm text-muted-foreground">Verifying session...</p>
        </div>
      </div>
    );
  }

  return (
    <Routes>
      <Route path="/login" element={isAuthenticated ? <Navigate to="/" /> : <Login />} />
      <Route element={isAuthenticated ? <Layout /> : <Navigate to="/login" />}>
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
    </Routes>
  );
}

export default App;
