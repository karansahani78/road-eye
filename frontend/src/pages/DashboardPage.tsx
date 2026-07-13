import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import api from '../services/api';
import { ApiResponse, DashboardStats } from '../types';

const DashboardPage: React.FC = () => {
  const navigate = useNavigate();

  const { data: stats, isLoading, isError } = useQuery({
    queryKey: ['dashboard'],
    queryFn: async () => {
      const response = await api.get<ApiResponse<DashboardStats>>('/analytics/dashboard');
      return response.data.data;
    },
  });

  if (isLoading) return <div className="loading">Loading dashboard...</div>;
  if (isError) return <div className="error-message">Failed to load dashboard data.</div>;

  const cameraHealth = stats ? Math.round(((stats.onlineCameras || 0) / Math.max(stats.totalCameras || 1, 1)) * 100) : 0;

  return (
    <div className="page">
      <div className="page-header">
        <div>
          <h2>Command Center</h2>
          <p style={{ color: 'var(--text-muted)', fontSize: '13px', marginTop: '4px' }}>
            Real-time traffic surveillance overview
          </p>
        </div>
      </div>

      <div className="stats-grid" style={{ marginBottom: '24px' }}>
        <div className="stat-card" onClick={() => navigate('/cameras')}>
          <div className="stat-value">{stats?.totalCameras ?? 0}</div>
          <div className="stat-label">Total Cameras</div>
        </div>
        <div className="stat-card" onClick={() => navigate('/cameras')} style={{ borderTopColor: 'var(--success)' }}>
          <div className="stat-value" style={{ color: 'var(--success)' }}>{stats?.onlineCameras ?? 0}</div>
          <div className="stat-label">Online</div>
        </div>
        <div className="stat-card" onClick={() => navigate('/cameras')} style={{ borderTopColor: 'var(--danger)' }}>
          <div className="stat-value" style={{ color: 'var(--danger)' }}>{stats?.offlineCameras ?? 0}</div>
          <div className="stat-label">Offline</div>
        </div>
        <div className="stat-card" onClick={() => navigate('/analytics')} style={{ borderTopColor: 'var(--warning)' }}>
          <div className="stat-value" style={{ color: 'var(--warning)' }}>{stats?.vehiclesDetected ?? 0}</div>
          <div className="stat-label">Vehicles Detected</div>
        </div>
        <div className="stat-card" onClick={() => navigate('/violations')} style={{ borderTopColor: '#a855f7' }}>
          <div className="stat-value" style={{ color: '#a855f7' }}>{stats?.activeViolations ?? 0}</div>
          <div className="stat-label">Active Violations</div>
        </div>
        <div className="stat-card" onClick={() => navigate('/violations')} style={{ borderTopColor: '#f97316' }}>
          <div className="stat-value" style={{ color: '#f97316' }}>{stats?.pendingReviews ?? 0}</div>
          <div className="stat-label">Pending Review</div>
        </div>
      </div>

      <div className="detail-grid">
        <div className="detail-card">
          <h3>System Status</h3>
          <dl>
            <dt>Active Streams</dt>
            <dd>{stats?.activeStreams ?? 0} / {stats?.totalCameras ?? 0}</dd>
            <dt>Camera Health</dt>
            <dd>
              <span style={{
                color: cameraHealth >= 80 ? 'var(--success)' : cameraHealth >= 50 ? 'var(--warning)' : 'var(--danger)',
                fontWeight: 600,
              }}>
                {cameraHealth}%
              </span>
            </dd>
            <dt>Critical Alerts</dt>
            <dd style={{ color: (stats?.activeCriticalAlerts ?? 0) > 0 ? 'var(--danger)' : 'var(--text)' }}>
              {stats?.activeCriticalAlerts ?? 0}
            </dd>
            <dt>Confirmed Violations</dt>
            <dd>{stats?.confirmedViolations ?? 0}</dd>
          </dl>
        </div>
        <div className="detail-card">
          <h3>Quick Actions</h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginTop: '12px' }}>
            <button className="btn-primary" onClick={() => navigate('/live')} style={{ width: '100%', textAlign: 'left' }}>
              Open Live Monitoring
            </button>
            <button className="btn-secondary" onClick={() => navigate('/cameras')} style={{ width: '100%', textAlign: 'left' }}>
              Manage Cameras
            </button>
            <button className="btn-secondary" onClick={() => navigate('/incidents')} style={{ width: '100%', textAlign: 'left' }}>
              View Incidents
            </button>
            <button className="btn-secondary" onClick={() => navigate('/analytics')} style={{ width: '100%', textAlign: 'left' }}>
              Analytics Dashboard
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default DashboardPage;
