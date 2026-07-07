import React, { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
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

  if (isLoading) return <div className="loading">Loading...</div>;
  if (!camera) return <div className="error">Camera not found</div>;

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
          <dl>
            <dt>Code</dt><dd>{camera.cameraCode}</dd>
            <dt>Type</dt><dd>{camera.sourceType}</dd>
            <dt>Location</dt><dd>{camera.location || '-'}</dd>
            <dt>Coordinates</dt><dd>{camera.latitude}, {camera.longitude}</dd>
            <dt>Direction</dt><dd>{camera.direction || '-'}</dd>
            <dt>Processing FPS</dt><dd>{camera.processingFps}</dd>
            <dt>Resolution</dt><dd>{camera.resolution || '-'}</dd>
          </dl>
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
