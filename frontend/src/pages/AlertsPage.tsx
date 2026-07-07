import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import api from '../services/api';
import { Alert, ApiResponse, PagedResponse } from '../types';
import { useAlertWebSocket } from '../hooks/useAlertWebSocket';

const AlertsPage: React.FC = () => {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [severityFilter, setSeverityFilter] = useState('');

  useAlertWebSocket();

  const { data, isLoading } = useQuery({
    queryKey: ['alerts', page, severityFilter],
    queryFn: async () => {
      const response = await api.get<ApiResponse<PagedResponse<Alert>>>('/alerts', {
        params: { page, size: 20, sort: 'createdAt,desc', severity: severityFilter || undefined },
      });
      return response.data.data;
    },
  });

  const acknowledgeMutation = useMutation({
    mutationFn: async (id: string) => {
      await api.patch(`/alerts/${id}/acknowledge`);
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['alerts'] }),
  });

  const resolveMutation = useMutation({
    mutationFn: async (id: string) => {
      await api.patch(`/alerts/${id}/resolve`);
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['alerts'] }),
  });

  const severityClass = (severity: string) => `severity-${severity.toLowerCase()}`;

  return (
    <div className="alerts-page">
      <div className="page-header">
        <h2>Alerts</h2>
        <div className="filter-bar">
          <select value={severityFilter} onChange={(e) => { setSeverityFilter(e.target.value); setPage(0); }}>
            <option value="">All Severities</option>
            <option value="CRITICAL">Critical</option>
            <option value="HIGH">High</option>
            <option value="MEDIUM">Medium</option>
            <option value="LOW">Low</option>
          </select>
        </div>
      </div>

      {isLoading ? (
        <div className="loading">Loading alerts...</div>
      ) : (
        <>
          <div className="alert-list">
            {data?.content?.map((alert: Alert) => (
              <div key={alert.id} className={`alert-card ${severityClass(alert.severity)}`}>
                <div className="alert-header">
                  <span className={`severity-badge ${severityClass(alert.severity)}`}>{alert.severity}</span>
                  <span className="alert-type">{alert.type}</span>
                  <span className="alert-time">{new Date(alert.createdAt).toLocaleString()}</span>
                </div>
                <div className="alert-body">
                  <p>{alert.message}</p>
                  <span className="alert-source">{alert.cameraName || alert.cameraId}</span>
                </div>
                <div className="alert-actions">
                  {!alert.acknowledged && (
                    <button className="btn-sm btn-secondary" onClick={() => acknowledgeMutation.mutate(alert.id)}>
                      Acknowledge
                    </button>
                  )}
                  {!alert.resolved && (
                    <button className="btn-sm btn-primary" onClick={() => resolveMutation.mutate(alert.id)}>
                      Resolve
                    </button>
                  )}
                  {alert.acknowledged && <span className="text-muted">Acknowledged</span>}
                  {alert.resolved && <span className="text-muted">Resolved</span>}
                </div>
              </div>
            ))}
            {(!data?.content || data.content.length === 0) && (
              <p className="empty-state">No alerts</p>
            )}
          </div>
          {data && (
            <div className="pagination">
              <button disabled={data.first} onClick={() => setPage(page - 1)}>Previous</button>
              <span>Page {data.page + 1} of {data.totalPages}</span>
              <button disabled={data.last} onClick={() => setPage(page + 1)}>Next</button>
            </div>
          )}
        </>
      )}
    </div>
  );
};

export default AlertsPage;
