import React, { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { MapContainer, TileLayer, Marker, useMapEvents } from 'react-leaflet';
import L from 'leaflet';
import { cameraService } from '../services/cameraService';

const CameraDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [calibrationData, setCalibrationData] = useState({
    knownDistance: 10,
    referencePoint1: '',
    referencePoint2: '',
    algorithmVersion: 'roadeye-cv-v1',
    calibrationData: '{}',
  });
  const [editing, setEditing] = useState(false);
  const [editData, setEditData] = useState({
    name: '',
    description: '',
    location: '',
    latitude: 0,
    longitude: 0,
    direction: '',
    processingFps: 15,
    active: true,
  });

  const LocationPicker: React.FC<{ lat: number; lng: number; onChange: (lat: number, lng: number) => void }> = ({ lat, lng, onChange }) => {
    const markerIcon = L.divIcon({
      className: 'custom-marker-icon',
      html: `<div style="width:20px;height:20px;background:#1e40af;border:3px solid #fff;border-radius:50%;box-shadow:0 2px 6px rgba(0,0,0,0.3);"></div>`,
      iconSize: [20, 20],
      iconAnchor: [10, 10],
    });

    const MapClickHandler = () => {
      useMapEvents({
        click(e) {
          onChange(e.latlng.lat, e.latlng.lng);
        },
      });
      return null;
    };

    const center: [number, number] = (lat !== 0 && lng !== 0) ? [lat, lng] : [27.7172, 85.3240];

    return (
      <div className="map-picker">
        <MapContainer center={center} zoom={lat !== 0 ? 15 : 12} className="leaflet-map">
          <TileLayer
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          />
          <MapClickHandler />
          {lat !== 0 && lng !== 0 && (
            <Marker position={[lat, lng]} icon={markerIcon} />
          )}
        </MapContainer>
      </div>
    );
  };

  const { data: camera, isLoading } = useQuery({
    queryKey: ['camera', id],
    queryFn: () => cameraService.getById(id!),
    enabled: !!id,
  });

  const { data: calibration } = useQuery({
    queryKey: ['camera-calibration', id],
    queryFn: () => cameraService.getCalibration(id!),
    enabled: !!id,
  });

  const { data: healthData } = useQuery({
    queryKey: ['camera-health', id],
    queryFn: () => cameraService.getHealth(id!),
    enabled: !!id,
  });

  const calibrateMutation = useMutation({
    mutationFn: (data: any) => cameraService.saveCalibration(id!, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['camera-calibration', id] });
      alert('Calibration saved');
    },
  });

  const testConnectionMutation = useMutation({
    mutationFn: () => cameraService.testConnection(id!),
  });

  const updateMutation = useMutation({
    mutationFn: (data: any) => cameraService.update(id!, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['camera', id] });
      queryClient.invalidateQueries({ queryKey: ['cameras'] });
      setEditing(false);
      alert('Camera updated');
    },
  });

  if (isLoading) return <div className="loading">Loading...</div>;
  if (!camera) return <div className="error">Camera not found</div>;

  const startEditing = () => {
    setEditData({
      name: camera.name,
      description: camera.description || '',
      location: camera.location || '',
      latitude: camera.latitude || 0,
      longitude: camera.longitude || 0,
      direction: camera.direction || '',
      processingFps: camera.processingFps || 15,
      active: camera.active,
    });
    setEditing(true);
  };

  const handleUpdate = (e: React.FormEvent) => {
    e.preventDefault();
    updateMutation.mutate({
      cameraCode: camera.cameraCode,
      sourceType: camera.sourceType,
      ...editData,
    });
  };

  const handleCalibrate = (e: React.FormEvent) => {
    e.preventDefault();
    calibrateMutation.mutate(calibrationData);
  };

  return (
    <div className="camera-detail-page">
      <button className="btn-secondary" onClick={() => navigate('/cameras')}>← Back to Cameras</button>
      <div className="detail-header">
        <h2>{camera.name}</h2>
        <span className={`status-badge ${camera.healthStatus === 'ONLINE' ? 'status-online' : 'status-offline'}`}>
          {camera.healthStatus}
        </span>
      </div>

      <div className="detail-grid">
        <div className="detail-card">
          <h3>Camera Information</h3>
          {editing ? (
            <form onSubmit={handleUpdate}>
              <div className="form-group">
                <label>Name</label>
                <input value={editData.name} onChange={(e) => setEditData({ ...editData, name: e.target.value })} required />
              </div>
              <div className="form-group">
                <label>Location</label>
                <input value={editData.location} onChange={(e) => setEditData({ ...editData, location: e.target.value })} />
              </div>
              <div className="form-group">
                <label>Latitude</label>
                <input type="number" step="any" value={editData.latitude}
                  onChange={(e) => setEditData({ ...editData, latitude: parseFloat(e.target.value) || 0 })} />
              </div>
              <div className="form-group">
                <label>Longitude</label>
                <input type="number" step="any" value={editData.longitude}
                  onChange={(e) => setEditData({ ...editData, longitude: parseFloat(e.target.value) || 0 })} />
              </div>
              <div className="form-group full-width">
                <label>Click on map to set coordinates</label>
                <LocationPicker
                  lat={editData.latitude}
                  lng={editData.longitude}
                  onChange={(lat, lng) => setEditData(prev => ({ ...prev, latitude: lat, longitude: lng }))}
                />
                <div className="coord-display">
                  Lat: {editData.latitude.toFixed(6)}, Lng: {editData.longitude.toFixed(6)}
                </div>
              </div>
              <div className="form-group">
                <label>FPS</label>
                <input type="number" value={editData.processingFps}
                  onChange={(e) => setEditData({ ...editData, processingFps: parseInt(e.target.value) || 15 })} />
              </div>
              <div className="form-group">
                <label>
                  <input type="checkbox" checked={editData.active}
                    onChange={(e) => setEditData({ ...editData, active: e.target.checked })} />
                  {' '}Active
                </label>
              </div>
              <button type="submit" className="btn-primary" disabled={updateMutation.isPending}>Save</button>
              <button type="button" className="btn-secondary" onClick={() => setEditing(false)}>Cancel</button>
            </form>
          ) : (
            <>
              <dl>
                <dt>Code</dt><dd>{camera.cameraCode}</dd>
                <dt>Type</dt><dd>{camera.sourceType}</dd>
                <dt>Location</dt><dd>{camera.location || '-'}</dd>
                <dt>Coordinates</dt><dd>{camera.latitude}, {camera.longitude}</dd>
                <dt>Direction</dt><dd>{camera.direction || '-'}</dd>
                <dt>Processing FPS</dt><dd>{camera.processingFps}</dd>
                <dt>Resolution</dt><dd>{camera.resolution || '-'}</dd>
                <dt>Active</dt><dd>{camera.active ? 'Yes' : 'No'}</dd>
              </dl>
              <button className="btn-secondary" onClick={startEditing}>Edit Camera</button>
            </>
          )}
        </div>

        <div className="detail-card">
          <h3>Calibration</h3>
          {calibration ? (
            <dl>
              <dt>Version</dt><dd>{calibration.calibrationVersion}</dd>
              <dt>Distance</dt><dd>{calibration.knownDistance}m</dd>
              <dt>Algorithm</dt><dd>{calibration.algorithmVersion}</dd>
              <dt>Calibrated By</dt><dd>{calibration.calibratedBy}</dd>
              <dt>Calibrated At</dt><dd>{calibration.calibratedAt ? new Date(calibration.calibratedAt).toLocaleString() : '-'}</dd>
            </dl>
          ) : (
            <p className="text-muted">Not calibrated</p>
          )}
          <form onSubmit={handleCalibrate} className="calibration-form">
            <h4>Calibrate Camera</h4>
            <div className="form-group">
              <label>Known Distance (meters)</label>
              <input type="number" step="0.1" value={calibrationData.knownDistance}
                onChange={(e) => setCalibrationData({...calibrationData, knownDistance: parseFloat(e.target.value) || 0})} />
            </div>
            <div className="form-group">
              <label>Reference Point 1 (x,y)</label>
              <input value={calibrationData.referencePoint1}
                onChange={(e) => setCalibrationData({...calibrationData, referencePoint1: e.target.value})} placeholder="100,200" />
            </div>
            <div className="form-group">
              <label>Reference Point 2 (x,y)</label>
              <input value={calibrationData.referencePoint2}
                onChange={(e) => setCalibrationData({...calibrationData, referencePoint2: e.target.value})} placeholder="300,400" />
            </div>
            <button type="submit" className="btn-primary" disabled={calibrateMutation.isPending}>
              {calibrateMutation.isPending ? 'Saving...' : 'Save Calibration'}
            </button>
          </form>
        </div>

        <div className="detail-card">
          <h3>Health Events</h3>
          {healthData?.content?.length ? (
            <table className="data-table">
              <thead>
                <tr><th>Status</th><th>Recorded At</th><th>Details</th></tr>
              </thead>
              <tbody>
                {healthData.content.slice(0, 5).map((event) => (
                  <tr key={event.id}>
                    <td>{event.status || '-'}</td>
                    <td>{event.recordedAt ? new Date(event.recordedAt).toLocaleString() : '-'}</td>
                    <td>{event.message || '-'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            <p className="text-muted">No health events recorded</p>
          )}
        </div>

        <div className="detail-card">
          <h3>Actions</h3>
          <button className="btn-secondary" onClick={() => testConnectionMutation.mutate()}
            disabled={testConnectionMutation.isPending}>
            {testConnectionMutation.isPending ? 'Testing...' : 'Test Connection'}
          </button>
          {testConnectionMutation.data && (
            <pre className="test-result">{JSON.stringify(testConnectionMutation.data, null, 2)}</pre>
          )}
        </div>
      </div>
    </div>
  );
};

export default CameraDetailPage;
