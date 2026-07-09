import React, { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { violationService } from '../services/violationService';
import { ViolationEvidence } from '../types';
import AuthenticatedEvidenceImage from '../components/AuthenticatedEvidenceImage';
import api from '../services/api';

const statusTransitions: Record<string, string[]> = {
  'PENDING_REVIEW': ['UNDER_REVIEW', 'REJECTED'],
  'UNDER_REVIEW': ['CONFIRMED', 'REJECTED', 'NEEDS_ADDITIONAL_REVIEW'],
  'NEEDS_ADDITIONAL_REVIEW': ['UNDER_REVIEW', 'REJECTED'],
  'CONFIRMED': ['FORWARDED_TO_AUTHORITY', 'CLOSED'],
  'REJECTED': ['CLOSED'],
  'FORWARDED_TO_AUTHORITY': ['CLOSED'],
};

const ViolationDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [comment, setComment] = useState('');
  const [selectedStatus, setSelectedStatus] = useState('');

  const { data: violation, isLoading } = useQuery({
    queryKey: ['violation', id],
    queryFn: () => violationService.getById(id!),
    enabled: !!id,
  });

  const { data: evidenceItems } = useQuery({
    queryKey: ['violation-evidence', id],
    queryFn: () => violationService.getEvidence(id!),
    enabled: !!id,
  });

  const updateMutation = useMutation({
    mutationFn: ({ status, comment }: { status: string; comment?: string }) =>
      violationService.updateStatus(id!, status, comment),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['violation', id] });
      queryClient.invalidateQueries({ queryKey: ['violations'] });
      setComment('');
      setSelectedStatus('');
      alert('Status updated');
    },
  });

  const handleDownload = async (evidenceId: string) => {
    try {
      const response = await api.get(`/evidence/${evidenceId}/download`, { responseType: 'blob' });
      const url = URL.createObjectURL(response.data);
      const link = document.createElement('a');
      link.href = url;
      link.download = `evidence-${evidenceId}`;
      link.click();
      URL.revokeObjectURL(url);
    } catch {
      alert('Failed to download evidence');
    }
  };

  const handleStatusUpdate = () => {
    if (selectedStatus) {
      updateMutation.mutate({ status: selectedStatus, comment: comment || undefined });
    }
  };

  if (isLoading) return <div className="loading">Loading...</div>;
  if (!violation) return <div className="error">Violation not found</div>;

  const availableTransitions = statusTransitions[violation.status] || [];

  return (
    <div className="violation-detail-page">
      <button className="btn-secondary" onClick={() => navigate('/violations')}>← Back</button>
      <div className="detail-header">
        <h2>Violation Review</h2>
        <span className={`status-badge status-${violation.status.toLowerCase()}`}>{violation.status}</span>
      </div>

      <div className="detail-grid">
        <div className="detail-card">
          <h3>Violation Details</h3>
          <dl>
            <dt>Type</dt><dd>{violation.violationType}</dd>
            <dt>Camera</dt><dd>{violation.cameraName || violation.cameraId}</dd>
            <dt>Timestamp</dt><dd>{new Date(violation.timestamp).toLocaleString()}</dd>
            <dt>Confidence</dt><dd>{(violation.confidence * 100).toFixed(1)}%</dd>
            <dt>Vehicle Category</dt><dd>{violation.vehicleCategory || '-'}</dd>
            <dt>Plate</dt><dd>{violation.normalizedPlate || '-'} ({violation.plateCountry || '-'})</dd>
            <dt>Speed</dt><dd>{violation.speedKmh ? `${violation.speedKmh.toFixed(1)} km/h` : '-'}</dd>
            <dt>Speed Limit</dt><dd>{violation.speedLimit ? `${violation.speedLimit} km/h` : '-'}</dd>
          </dl>
        </div>

        <div className="detail-card">
          <h3>Review Actions</h3>
          {availableTransitions.length > 0 ? (
            <>
              <div className="form-group">
                <label>Update Status</label>
                <select value={selectedStatus} onChange={(e) => setSelectedStatus(e.target.value)}>
                  <option value="">Select status...</option>
                  {availableTransitions.map((s) => (
                    <option key={s} value={s}>{s}</option>
                  ))}
                </select>
              </div>
              <div className="form-group">
                <label>Comment</label>
                <textarea value={comment} onChange={(e) => setComment(e.target.value)}
                  placeholder="Reason for status change..." rows={3} />
              </div>
              <button className="btn-primary" onClick={handleStatusUpdate}
                disabled={!selectedStatus || updateMutation.isPending}>
                {updateMutation.isPending ? 'Updating...' : 'Update Status'}
              </button>
            </>
          ) : (
            <p className="text-muted">No further actions available</p>
          )}
        </div>

        <div className="detail-card full-width">
          <h3>Evidence ({evidenceItems?.length || 0})</h3>
          <div className="evidence-viewer">
            {evidenceItems && evidenceItems.length > 0 ? (
              <div className="evidence-grid">
                {evidenceItems.map((ev: ViolationEvidence) => (
                  <div key={ev.id} className="evidence-item">
                    {ev.evidenceType === 'IMAGE' || ev.evidenceType === 'FRAME' ? (
                      <AuthenticatedEvidenceImage evidence={ev} />
                    ) : (
                      <div className="evidence-icon">{ev.evidenceType}</div>
                    )}
                    <div className="evidence-meta">
                      <span className="evidence-type">{ev.evidenceType}</span>
                      {ev.fileSize && (
                        <span className="evidence-size">
                          {ev.fileSize > 1048576
                            ? (ev.fileSize / 1048576).toFixed(1) + ' MB'
                            : ev.fileSize > 1024
                              ? (ev.fileSize / 1024).toFixed(1) + ' KB'
                              : ev.fileSize + ' B'}
                        </span>
                      )}
                      {ev.objectKey && (
                        <button type="button" className="evidence-download btn-sm btn-secondary"
                          onClick={() => handleDownload(ev.id)}>Download</button>
                      )}
                    </div>
                    {ev.metadata && (
                      <pre className="evidence-metadata">{JSON.stringify(JSON.parse(ev.metadata), null, 2)}</pre>
                    )}
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-muted">No evidence available for this violation.</p>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default ViolationDetailPage;
