import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { violationService } from '../services/violationService';
import { Violation } from '../types';

const ViolationsPage: React.FC = () => {
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const [statusFilter, setStatusFilter] = useState('');

  const { data, isLoading } = useQuery({
    queryKey: ['violations', page, statusFilter],
    queryFn: () => violationService.getAll({
      page,
      size: 20,
      sort: 'createdAt,desc',
      status: statusFilter || undefined,
    }),
  });

  const violationTypeLabel = (type: string) => {
    return type.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
  };

  return (
    <div className="violations-page">
      <div className="page-header">
        <h2>Violations</h2>
        <div className="filter-bar">
          <select value={statusFilter} onChange={(e) => { setStatusFilter(e.target.value); setPage(0); }}>
            <option value="">All Status</option>
            <option value="PENDING_REVIEW">Pending Review</option>
            <option value="UNDER_REVIEW">Under Review</option>
            <option value="CONFIRMED">Confirmed</option>
            <option value="REJECTED">Rejected</option>
            <option value="CLOSED">Closed</option>
          </select>
        </div>
      </div>

      {isLoading ? (
        <div className="loading">Loading violations...</div>
      ) : (
        <>
          <div className="table-container">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Type</th>
                  <th>Camera</th>
                  <th>Timestamp</th>
                  <th>Vehicle</th>
                  <th>Plate</th>
                  <th>Speed</th>
                  <th>Confidence</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {data?.content?.map((v: Violation) => (
                  <tr key={v.id} onClick={() => navigate(`/violations/${v.id}`)} className="clickable-row">
                    <td><span className="violation-type">{violationTypeLabel(v.violationType)}</span></td>
                    <td>{v.cameraName || v.cameraId?.substring(0, 8)}</td>
                    <td>{new Date(v.timestamp).toLocaleString()}</td>
                    <td>{v.vehicleCategory || '-'}</td>
                    <td>{v.normalizedPlate || '-'}</td>
                    <td>{v.speedKmh ? `${v.speedKmh.toFixed(0)} km/h` : '-'}</td>
                    <td>{(v.confidence * 100).toFixed(0)}%</td>
                    <td><span className={`status-badge status-${v.status.toLowerCase()}`}>{v.status}</span></td>
                  </tr>
                ))}
                {(!data?.content || data.content.length === 0) && (
                  <tr><td colSpan={8} className="empty-table">No violations found</td></tr>
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

export default ViolationsPage;
