import { Routes, Route, Navigate } from 'react-router-dom';
import Layout from './components/layout/Layout';
import Dashboard from './pages/Dashboard';
import APIsPage from './pages/APIs';
import APIDetailPage from './pages/APIDetail';
import ProxiesPage from './pages/Proxies';
import ProxyDetailPage from './pages/ProxyDetail';
import APIKeysPage from './pages/APIKeys';
import AnalyticsPage from './pages/Analytics';
import LoginPage from './pages/Login';
import { useAuthStore } from './store/auth';

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const token = useAuthStore((s) => s.token);
  if (!token) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        path="/"
        element={
          <ProtectedRoute>
            <Layout />
          </ProtectedRoute>
        }
      >
        <Route index element={<Navigate to="/dashboard" replace />} />
        <Route path="dashboard" element={<Dashboard />} />
        <Route path="apis" element={<APIsPage />} />
        <Route path="apis/:id" element={<APIDetailPage />} />
        <Route path="proxies" element={<ProxiesPage />} />
        <Route path="proxies/:id" element={<ProxyDetailPage />} />
        <Route path="keys" element={<APIKeysPage />} />
        <Route path="analytics" element={<AnalyticsPage />} />
      </Route>
    </Routes>
  );
}
