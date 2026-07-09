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
  const [formData, setFormData] = useState({
    cameraCode: '',
    name: '',
    description: '',
    sourceType: 'RTSP',
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
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => cameraService.delete(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['cameras'] }),
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

  const handleDelete = async (id: string, e: React.MouseEvent) => {
    e.stopPropagation();
    if (window.confirm('Delete this camera?')) {
      deleteMutation.mutate(id);
    }
  };

  const healthStatusClass = (status: string) => {
    switch (status) {
      case 'ONLINE': return 'status-online';
      case 'OFFLINE': return 'status-offline';
      case 'DEGRADED': return 'status-degraded';
      default: return 'status-unknown';
    }
  };

  return (
    <div className="cameras-page">
      <div className="page-header">
        <h2>Cameras</h2>
        <button className="btn-primary" onClick={() => setShowForm(!showForm)}>
          {showForm ? 'Cancel' : 'Add Camera'}
        </button>
      </div>

      {showForm && (
        <form className="camera-form" onSubmit={handleSubmit}>
          <h3>Register New Camera</h3>
          <div className="form-grid">
            <div className="form-group">
              <label>Camera Code *</label>
              <input value={formData.cameraCode} onChange={(e) => setFormData({...formData, cameraCode: e.target.value})} required />
            </div>
            <div className="form-group">
              <label>Name *</label>
              <input value={formData.name} onChange={(e) => setFormData({...formData, name: e.target.value})} required />
            </div>
            <div className="form-group">
              <label>Source Type</label>
              <select value={formData.sourceType} onChange={(e) => setFormData({...formData, sourceType: e.target.value})}>
                <option value="RTSP">RTSP</option>
                <option value="VIDEO_FILE">Video File</option>
                <option value="CCTV">CCTV</option>
                <option value="IP_CAMERA">IP Camera</option>
              </select>
            </div>
            <div className="form-group">
              <label>Source URL *</label>
              <input value={formData.sourceUrl} onChange={(e) => setFormData({...formData, sourceUrl: e.target.value})} required placeholder="rtsp:// or file://" />
            </div>
            <div className="form-group">
              <label>Location</label>
              <input value={formData.location} onChange={(e) => setFormData({...formData, location: e.target.value})} />
            </div>
            <div className="form-group full-width">
              <CameraLocationSection location={cameraLocation} onChange={setCameraLocation} />
            </div>
            <div className="form-group">
              <label>FPS</label>
              <input type="number" value={formData.processingFps} onChange={(e) => setFormData({...formData, processingFps: parseInt(e.target.value) || 15})} />
            </div>
          </div>
          <button type="submit" className="btn-primary" disabled={createMutation.isPending}>
            {createMutation.isPending ? 'Creating...' : 'Create Camera'}
          </button>
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
                  <th>FPS</th>
                  <th>Last Heartbeat</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {data?.content?.map((camera: Camera) => (
                  <tr key={camera.id} onClick={() => navigate(`/cameras/${camera.id}`)} className="clickable-row">
                    <td>{camera.cameraCode}</td>
                    <td>{camera.name}</td>
                    <td>{camera.sourceType}</td>
                    <td>{camera.location || '-'}</td>
                    <td><span className={`status-badge ${healthStatusClass(camera.healthStatus)}`}>{camera.healthStatus}</span></td>
                    <td>{camera.processingFps}</td>
                    <td>{camera.lastHeartbeat ? new Date(camera.lastHeartbeat).toLocaleString() : '-'}</td>
                    <td>
                      <button className="btn-danger btn-sm" onClick={(e) => handleDelete(camera.id, e)}>Delete</button>
                    </td>
                  </tr>
                ))}
                {(!data?.content || data.content.length === 0) && (
                  <tr><td colSpan={8} className="empty-table">No cameras registered</td></tr>
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
