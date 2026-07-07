import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './hooks/useAuth';
import ProtectedRoute from './components/ProtectedRoute';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import CamerasPage from './pages/CamerasPage';
import CameraDetailPage from './pages/CameraDetailPage';
import ViolationsPage from './pages/ViolationsPage';
import ViolationDetailPage from './pages/ViolationDetailPage';
import AlertsPage from './pages/AlertsPage';
import MapPage from './pages/MapPage';
import AnalyticsPage from './pages/AnalyticsPage';
import UsersPage from './pages/UsersPage';
import AuditLogsPage from './pages/AuditLogsPage';
import SystemHealthPage from './pages/SystemHealthPage';
import RoadsPage from './pages/RoadsPage';
import Layout from './components/Layout';

const App: React.FC = () => {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<ProtectedRoute />}>
          <Route element={<Layout />}>
            <Route path="/" element={<Navigate to="/dashboard" replace />} />
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/cameras" element={<CamerasPage />} />
            <Route path="/cameras/:id" element={<CameraDetailPage />} />
            <Route path="/roads" element={<RoadsPage />} />
            <Route path="/violations" element={<ViolationsPage />} />
            <Route path="/violations/:id" element={<ViolationDetailPage />} />
            <Route path="/alerts" element={<AlertsPage />} />
            <Route path="/map" element={<MapPage />} />
            <Route path="/analytics" element={<AnalyticsPage />} />
            <Route path="/users" element={<UsersPage />} />
            <Route path="/audit-logs" element={<AuditLogsPage />} />
            <Route path="/system-health" element={<SystemHealthPage />} />
          </Route>
        </Route>
      </Routes>
    </AuthProvider>
  );
};

export default App;
