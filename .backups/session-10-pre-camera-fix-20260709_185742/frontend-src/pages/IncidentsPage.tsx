import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { incidentService } from '../services/incidentService';
import { Incident } from '../types';

const SEVERITY_COLORS: Record<string, string> = {
  CRITICAL: 'severity-critical',
  HIGH: 'severity-high',
  MEDIUM: 'severity-medium',
  LOW: 'severity-low',
};

const STATUS_COLORS: Record<string, string> = {
  DETECTED: 'status-detected',
  ASSIGNED: 'status-assigned',
  RESOLVED: 'status-resolved',
  CLOSED: 'status-closed',
};

const IncidentsPage: React.FC = () => {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [filterStatus, setFilterStatus] = useState('');
  const [filterSeverity, setFilterSeverity] = useState('');
  const [selected, setSelected] = useState<Incident | null>(null);
  const [assigneeName, setAssigneeName] = useState('');
  const [resolveNotes, setResolveNotes] = useState('');

  const params: any = { page, size: 20 };
  if (filterStatus) params.status = filterStatus;
  if (filterSeverity) params.severity = filterSeverity;

  const { data, isLoading } = useQuery({
    queryKey: ['incidents', params],
    queryFn: () => incidentService.getAll(params),
  });

  const assignMutation = useMutation({
    mutationFn: (id: string) => incidentService.assign(id, assigneeName || undefined),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['incidents'] }); setSelected(null); setAssigneeName(''); },
  });

  const resolveMutation = useMutation({
    mutationFn: (id: string) => incidentService.resolve(id, resolveNotes || undefined),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['incidents'] }); setSelected(null); setResolveNotes(''); },
  });

  return (
    <div className="page">
      <div className="page-header">
        <h1>Incidents</h1>
        <div className="filters">
          <select value={filterStatus} onChange={e => { setFilterStatus(e.target.value); setPage(0); }}>
            <option value="">All Statuses</option>
            <option value="DETECTED">Detected</option>
            <option value="ASSIGNED">Assigned</option>
            <option value="RESOLVED">Resolved</option>
            <option value="CLOSED">Closed</option>
          </select>
          <select value={filterSeverity} onChange={e => { setFilterSeverity(e.target.value); setPage(0); }}>
            <option value="">All Severities</option>
            <option value="CRITICAL">Critical</option>
            <option value="HIGH">High</option>
            <option value="MEDIUM">Medium</option>
            <option value="LOW">Low</option>
          </select>
          <span className="total-count">{data?.totalElements || 0} incidents</span>
        </div>
      </div>

      {isLoading ? <div className="loading">Loading incidents...</div> : (
        <>
          <table className="data-table">
            <thead>
              <tr>
                <th>Type</th>
                <th>Severity</th>
                <th>Status</th>
                <th>Camera</th>
                <th>Description</th>
                <th>Assigned To</th>
                <th>Detected At</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {data?.content?.length === 0 ? (
                <tr><td colSpan={8} className="empty-state">No incidents found.</td></tr>
              ) : data?.content?.map((inc: Incident) => (
                <tr key={inc.id}>
                  <td><strong>{inc.incidentType}</strong></td>
                  <td><span className={`badge ${SEVERITY_COLORS[inc.severity] || ''}`}>{inc.severity}</span></td>
                  <td><span className={`badge ${STATUS_COLORS[inc.status] || ''}`}>{inc.status}</span></td>
                  <td>{inc.cameraName || inc.cameraId?.substring(0, 8)}</td>
                  <td className="cell-truncate">{inc.description}</td>
                  <td>{inc.assignedTo || '-'}</td>
                  <td>{inc.detectedAt ? new Date(inc.detectedAt).toLocaleString() : '-'}</td>
                  <td>
                    <button className="btn-sm btn-secondary" onClick={() => setSelected(inc)}>Manage</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          {data && data.totalPages > 1 && (
            <div className="pagination">
              <button disabled={data.first} onClick={() => setPage(p => Math.max(0, p - 1))}>Previous</button>
              <span>Page {data.page + 1} of {data.totalPages}</span>
              <button disabled={data.last} onClick={() => setPage(p => p + 1)}>Next</button>
            </div>
          )}
        </>
      )}

      {selected && (
        <div className="modal-overlay" onClick={() => setSelected(null)}>
          <div className="modal" onClick={e => e.stopPropagation()}>
            <div className="modal-header">
              <h2>{selected.incidentType}</h2>
              <button className="modal-close" onClick={() => setSelected(null)}>×</button>
            </div>
            <div className="modal-body">
              <div className="detail-grid">
                <div className="detail-item"><label>Status</label><span>{selected.status}</span></div>
                <div className="detail-item"><label>Severity</label><span>{selected.severity}</span></div>
                <div className="detail-item"><label>Camera</label><span>{selected.cameraName || selected.cameraId}</span></div>
                <div className="detail-item"><label>Description</label><span>{selected.description || '-'}</span></div>
                <div className="detail-item"><label>Assigned To</label><span>{selected.assignedTo || 'Unassigned'}</span></div>
                <div className="detail-item"><label>Source</label><span>{selected.source}</span></div>
                <div className="detail-item"><label>Confidence</label><span>{selected.confidence ? `${(selected.confidence * 100).toFixed(0)}%` : '-'}</span></div>
                <div className="detail-item"><label>Detected At</label><span>{selected.detectedAt ? new Date(selected.detectedAt).toLocaleString() : '-'}</span></div>
              </div>

              {selected.status !== 'RESOLVED' && selected.status !== 'CLOSED' && (
                <div className="modal-actions">
                  {(!selected.assignedTo || selected.assignedTo === '') && (
                    <div className="action-row">
                      <input type="text" placeholder="Assignee name" value={assigneeName} onChange={e => setAssigneeName(e.target.value)} />
                      <button className="btn-primary" onClick={() => assignMutation.mutate(selected.id)} disabled={assignMutation.isPending}>
                        {assignMutation.isPending ? 'Assigning...' : 'Assign'}
                      </button>
                    </div>
                  )}
                  <div className="action-row">
                    <textarea placeholder="Resolution notes (optional)" value={resolveNotes} onChange={e => setResolveNotes(e.target.value)} rows={2} />
                    <button className="btn-success" onClick={() => resolveMutation.mutate(selected.id)} disabled={resolveMutation.isPending}>
                      {resolveMutation.isPending ? 'Resolving...' : 'Resolve'}
                    </button>
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default IncidentsPage;
