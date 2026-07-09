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

  const cards = [
    { label: 'Total Cameras', value: stats?.totalCameras ?? 0, color: '#3b82f6', link: '/cameras' },
    { label: 'Online Cameras', value: stats?.onlineCameras ?? 0, color: '#22c55e', link: '/cameras' },
    { label: 'Offline Cameras', value: stats?.offlineCameras ?? 0, color: '#ef4444', link: '/cameras' },
    { label: 'Active Streams', value: stats?.activeStreams ?? 0, color: '#8b5cf6', link: '/cameras' },
    { label: 'Vehicles Detected', value: stats?.vehiclesDetected ?? 0, color: '#f59e0b', link: '/analytics' },
    { label: 'Active Violations', value: stats?.activeViolations ?? 0, color: '#ef4444', link: '/violations' },
    { label: 'Pending Reviews', value: stats?.pendingReviews ?? 0, color: '#f97316', link: '/violations' },
    { label: 'Active Alerts', value: stats?.activeCriticalAlerts ?? 0, color: '#dc2626', link: '/alerts' },
  ];

  return (
    <div className="dashboard-page">
      <h2>Dashboard</h2>
      <div className="stats-grid">
        {cards.map((card) => (
          <div
            key={card.label}
            className="stat-card"
            style={{ borderTopColor: card.color }}
            onClick={() => navigate(card.link)}
          >
            <div className="stat-value" style={{ color: card.color }}>
              {card.value.toLocaleString()}
            </div>
            <div className="stat-label">{card.label}</div>
          </div>
        ))}
      </div>
    </div>
  );
};

export default DashboardPage;
