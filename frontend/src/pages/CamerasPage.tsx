import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { cameraService } from '../services/cameraService';
import { Camera } from '../types';
import CameraLocationSection, { LocationState } from '../components/CameraLocationSection';

const CamerasPage: React.FC = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [showForm, setShowForm] = useState(false);
  const [showInactive, setShowInactive] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [formData, setFormData] = useState({
    cameraCode: '',
    name: '',
    description: '',
    sourceType: 'RTSP' as string,
    sourceUrl: '',
    location: '',
    direction: '',
    processingFps: 15,
    active: true,
  });

  const [cameraLocation, setCameraLocation] = useState<LocationState>({
    latitude: null,
    longitude: null,
    locationSource: 'UNKNOWN',
    locationAccuracyMeters: null,
    address: '',
  });

  const { data, isLoading } = useQuery({
    queryKey: ['cameras', page],
    queryFn: () => cameraService.getAll({ page, size: 10, sort: 'createdAt,desc' }),
  });

  const createMutation = useMutation({
    mutationFn: (data: any) => cameraService.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cameras'] });
      queryClient.invalidateQueries({ queryKey: ['traffic-map'] });
      setShowForm(false);
      resetForm();
      setError(null);
    },
    onError: (err: any) => {
      setError(err?.response?.data?.message || err?.message || 'Failed to create camera');
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => cameraService.delete(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cameras'] });
      setError(null);
    },
    onError: (err: any) => {
      setError(err?.response?.data?.message || err?.message || 'Failed to deactivate camera');
    },
  });

  const reactivateMutation = useMutation({
    mutationFn: (cam: Camera) => cameraService.update(cam.id, {
      cameraCode: cam.cameraCode,
      name: cam.name,
      description: cam.description || '',
      sourceType: cam.sourceType,
      location: cam.location || '',
      latitude: cam.latitude,
      longitude: cam.longitude,
      direction: cam.direction || '',
      processingFps: cam.processingFps || 15,
      active: true,
      locationSource: cam.locationSource || 'UNKNOWN',
      locationAccuracyMeters: cam.locationAccuracyMeters,
    }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['cameras'] });
      setError(null);
    },
    onError: (err: any) => {
      setError(err?.response?.data?.message || err?.message || 'Failed to reactivate camera');
    },
  });

  const resetForm = () => {
    setFormData({
      cameraCode: '', name: '', description: '', sourceType: 'RTSP',
      sourceUrl: '', location: '',
      direction: '', processingFps: 15, active: true,
    });
    setCameraLocation({
      latitude: null, longitude: null,
      locationSource: 'UNKNOWN', locationAccuracyMeters: null,
      address: '',
    });
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const payload = {
      ...formData,
      sourceUrl: formData.sourceUrl,
      latitude: cameraLocation.latitude,
      longitude: cameraLocation.longitude,
      locationSource: cameraLocation.locationSource,
      locationAccuracyMeters: cameraLocation.locationAccuracyMeters,
    };
    createMutation.mutate(payload);
  };

  const handleDelete = (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    if (window.confirm('Deactivate this camera? It will be hidden from live monitoring.')) {
      deleteMutation.mutate(id);
    }
  };

  const handleReactivate = (cam: Camera, e: React.MouseEvent) => {
    e.stopPropagation();
    if (window.confirm(`Reactivate camera "${cam.cameraCode}"?`)) {
      reactivateMutation.mutate(cam);
    }
  };

  const healthStatusClass = (status: string) => {
    switch (status) {
      case 'ONLINE': return 'status-online';
      case 'OFFLINE': return 'status-offline';
      case 'DEGRADED': return 'status-degraded';
      case 'ERROR': return 'status-error';
      case 'CONNECTING': return 'status-connecting';
      default: return 'status-unknown';
    }
  };

  const visibleCameras = showInactive
    ? (data?.content || [])
    : (data?.content?.filter(c => c.active) || []);

  const activeCount = data?.content?.filter(c => c.active).length || 0;
  const inactiveCount = (data?.content?.length || 0) - activeCount;

  return (
    <div className="cameras-page">
      <div className="page-header">
        <h2>Cameras</h2>
        <div className="toolbar">
          {inactiveCount > 0 && (
            <label className="toggle-inactive">
              <input type="checkbox" checked={showInactive}
                onChange={(e) => setShowInactive(e.target.checked)} />
              {' '}Show deactivated ({inactiveCount})
            </label>
          )}
          <button className="btn-primary" onClick={() => setShowForm(!showForm)}>
            {showForm ? 'Cancel' : 'Add Camera'}
          </button>
        </div>
      </div>

      {error && <div className="alert alert-error" onClick={() => setError(null)}>{error}</div>}

      {showForm && (
        <form className="camera-form" onSubmit={handleSubmit}>
          <h3>Register New Camera</h3>
          <p style={{ color: 'var(--text-muted)', fontSize: '13px', marginBottom: '16px' }}>
            Add a new camera to the surveillance network. The camera will start processing automatically after registration.
          </p>
          <div className="form-grid">
            <div className="form-group">
              <label>Camera Code *</label>
              <input value={formData.cameraCode} onChange={(e) => setFormData({...formData, cameraCode: e.target.value})} required placeholder="e.g., CAM-001" />
            </div>
            <div className="form-group">
              <label>Name *</label>
              <input value={formData.name} onChange={(e) => setFormData({...formData, name: e.target.value})} required placeholder="e.g., Main Street Camera" />
            </div>
            <div className="form-group">
              <label>Source Type</label>
              <select value={formData.sourceType} onChange={(e) => setFormData({...formData, sourceType: e.target.value as any})}>
                <option value="RTSP">RTSP Stream</option>
                <option value="VIDEO_FILE">Video File</option>
                <option value="CCTV">CCTV</option>
                <option value="IP_CAMERA">IP Camera</option>
              </select>
            </div>
            <div className="form-group">
              <label>Source URL *</label>
              <input value={formData.sourceUrl} onChange={(e) => {
                const url = e.target.value;
                setFormData({...formData, sourceUrl: url});
                // Auto-detect source type from URL
                if (url.startsWith('rtsp://')) setFormData({...formData, sourceUrl: url, sourceType: 'RTSP'});
                else if (url.startsWith('file://') || url.endsWith('.mp4') || url.endsWith('.avi')) setFormData({...formData, sourceUrl: url, sourceType: 'VIDEO_FILE'});
                else if (url.startsWith('http://') || url.startsWith('https://')) setFormData({...formData, sourceUrl: url, sourceType: 'IP_CAMERA'});
              }} required placeholder="rtsp://user:pass@ip:port/stream" />
            </div>
            <div className="form-group">
              <label>Description</label>
              <input value={formData.description || ''} onChange={(e) => setFormData({...formData, description: e.target.value})} placeholder="Optional description" />
            </div>
            <div className="form-group">
              <label>Location</label>
              <input value={formData.location} onChange={(e) => setFormData({...formData, location: e.target.value})} placeholder="e.g., Main St & 5th Ave" />
            </div>
            <div className="form-group full-width">
              <CameraLocationSection location={cameraLocation} onChange={setCameraLocation} />
            </div>
            <div className="form-group">
              <label>Processing FPS</label>
              <input type="number" min="1" max="30" value={formData.processingFps} onChange={(e) => setFormData({...formData, processingFps: parseInt(e.target.value) || 15})} />
              <span style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '4px', display: 'block' }}>
                Lower FPS reduces CPU usage. Default: 15
              </span>
            </div>
          </div>
          <div style={{ display: 'flex', gap: '8px', marginTop: '16px' }}>
            <button type="submit" className="btn-primary" disabled={createMutation.isPending}>
              {createMutation.isPending ? 'Creating...' : 'Create Camera'}
            </button>
            <button type="button" className="btn-secondary" onClick={() => { setShowForm(false); resetForm(); }}>
              Cancel
            </button>
          </div>
        </form>
      )}

      {isLoading ? (
        <div className="loading">Loading cameras...</div>
      ) : (
        <>
          <div className="table-container">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Code</th>
                  <th>Name</th>
                  <th>Type</th>
                  <th>Location</th>
                  <th>Status</th>
                  <th>Active</th>
                  <th>FPS</th>
                  <th>Last Heartbeat</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {visibleCameras.map((camera: Camera) => (
                  <tr key={camera.id}
                    onClick={() => navigate(`/cameras/${camera.id}`)}
                    className={`clickable-row ${camera.active ? '' : 'row-deactivated'}`}>
                    <td>{camera.cameraCode}</td>
                    <td>{camera.name}</td>
                    <td>{camera.sourceType}</td>
                    <td>{camera.location || '-'}</td>
                    <td>
                      {camera.active ? (
                        <span className={`status-badge ${healthStatusClass(camera.healthStatus)}`}>{camera.healthStatus}</span>
                      ) : (
                        <span className="status-badge status-deactivated">DEACTIVATED</span>
                      )}
                    </td>
                    <td>{camera.active ? 'Yes' : 'No'}</td>
                    <td>{camera.processingFps}</td>
                    <td>{camera.lastHeartbeat ? new Date(camera.lastHeartbeat).toLocaleString() : '-'}</td>
                    <td>
                      {camera.active ? (
                        <button className="btn-danger btn-sm" onClick={(e) => handleDelete(camera.id, e)}
                          disabled={deleteMutation.isPending}>
                          Delete
                        </button>
                      ) : (
                        <button className="btn-primary btn-sm" onClick={(e) => handleReactivate(camera, e)}
                          disabled={reactivateMutation.isPending}>
                          Reactivate
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
                {visibleCameras.length === 0 && (
                  <tr><td colSpan={9} className="empty-table">
                    <div style={{ padding: '20px' }}>
                      <p style={{ fontSize: '16px', fontWeight: 600, marginBottom: '8px' }}>
                        {showInactive ? 'No cameras registered' : 'No active cameras'}
                      </p>
                      <p style={{ fontSize: '13px', marginBottom: '12px' }}>
                        {showInactive
                          ? 'Register your first camera to start surveillance.'
                          : 'Toggle "Show deactivated" to view all cameras, or register a new one.'}
                      </p>
                      {!showInactive && (
                        <button className="btn-primary" onClick={() => setShowForm(true)}>
                          Add First Camera
                        </button>
                      )}
                    </div>
                  </td></tr>
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

export default CamerasPage;
