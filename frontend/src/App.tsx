import { Routes, Route, Navigate } from 'react-router-dom';
import { useAuthStore } from '@/stores/authStore';
import { Layout } from '@/components/layout/Layout';
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

function App() {
  const { isAuthenticated } = useAuthStore();

  return (
    <Routes>
      <Route path="/login" element={isAuthenticated ? <Navigate to="/" /> : <Login />} />
      <Route element={isAuthenticated ? <Layout /> : <Navigate to="/login" />}>
        <Route path="/" element={<Dashboard />} />
        <Route path="/data-sources" element={<DataSources />} />
        <Route path="/activities" element={<Activities />} />
        <Route path="/health" element={<Health />} />
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
