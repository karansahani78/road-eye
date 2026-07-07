import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import api from '../services/api';
import { AuditLog, ApiResponse, PagedResponse } from '../types';

const AuditLogsPage: React.FC = () => {
  const [page, setPage] = useState(0);
  const [actionFilter, setActionFilter] = useState('');

  const { data, isLoading } = useQuery({
    queryKey: ['audit-logs', page, actionFilter],
    queryFn: async () => {
      const response = await api.get<ApiResponse<PagedResponse<AuditLog>>>('/audit-logs', {
        params: { page, size: 20, sort: 'createdAt,desc', action: actionFilter || undefined },
      });
      return response.data.data;
    },
  });

  return (
    <div className="audit-logs-page">
      <div className="page-header">
        <h2>Audit Logs</h2>
        <div className="filter-bar">
          <input placeholder="Filter by action..." value={actionFilter}
            onChange={(e) => { setActionFilter(e.target.value); setPage(0); }} />
        </div>
      </div>

      {isLoading ? (
        <div className="loading">Loading audit logs...</div>
      ) : (
        <>
          <div className="table-container">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Timestamp</th>
                  <th>Actor</th>
                  <th>Action</th>
                  <th>Target Type</th>
                  <th>Target ID</th>
                  <th>Metadata</th>
                </tr>
              </thead>
              <tbody>
                {data?.content?.map((log: AuditLog) => (
                  <tr key={log.id}>
                    <td>{new Date(log.createdAt).toLocaleString()}</td>
                    <td>{log.actor}</td>
                    <td><code>{log.action}</code></td>
                    <td>{log.targetType}</td>
                    <td className="text-mono">{log.targetId?.substring(0, 8) || '-'}</td>
                    <td className="text-truncate">{log.metadata || '-'}</td>
                  </tr>
                ))}
                {(!data?.content || data.content.length === 0) && (
                  <tr><td colSpan={6} className="empty-table">No audit logs</td></tr>
                )}
              </tbody>
            </table>
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

export default AuditLogsPage;
