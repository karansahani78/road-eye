import React from 'react';
import { useQuery } from '@tanstack/react-query';
import api from '../services/api';

const SystemHealthPage: React.FC = () => {
  const { data: healthData, isLoading } = useQuery({
    queryKey: ['system-health'],
    queryFn: () => api.get('/system/health'),
    refetchInterval: 30000,
  });

  const { data: infoData } = useQuery({
    queryKey: ['system-info'],
    queryFn: () => api.get('/system/info'),
  });

  if (isLoading) return <div className="loading">Loading system health...</div>;

  const health = healthData?.data?.data || healthData?.data || {};
  const info = infoData?.data?.data || infoData?.data || {};

  const healthItems = [
    { label: 'Database', status: health.database?.status || 'UNKNOWN' },
    { label: 'Kafka', status: health.kafka?.status || 'UNKNOWN' },
    { label: 'MinIO', status: health.minio?.status || 'UNKNOWN' },
    { label: 'AI Engine', status: health.aiEngine?.status || 'UNKNOWN' },
  ];

  return (
    <div className="system-health-page">
      <h2>System Health</h2>
      <div className="health-grid">
        {healthItems.map((item) => (
          <div key={item.label} className={`health-card ${String(item.status).toLowerCase()}`}>
            <h3>{item.label}</h3>
            <span className={`health-status ${String(item.status).toLowerCase()}`}>
              {String(item.status).toUpperCase()}
            </span>
          </div>
        ))}
      </div>
      <div className="info-card">
        <h3>Application Info</h3>
        <dl>
          <dt>Name</dt><dd>{typeof info.application === 'string' ? info.application : info.name || 'RoadEye'}</dd>
          <dt>Version</dt><dd>{info.version || '0.1.0'}</dd>
        </dl>
      </div>
    </div>
  );
};

export default SystemHealthPage;
