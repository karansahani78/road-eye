import React, { useState, useCallback } from 'react';
import {
  MapContainer, TileLayer, Marker, Popup, useMap, Polyline, Circle
} from 'react-leaflet';
import { useQuery } from '@tanstack/react-query';
import L from 'leaflet';
import { trafficMapService, MapCamera, MapViolation, MapAlert, TrafficMapData } from '../services/trafficMapService';
import { useNavigate } from 'react-router-dom';

const healthColors: Record<string, string> = {
  ONLINE: '#22c55e',
  OFFLINE: '#ef4444',
  DEGRADED: '#f59e0b',
  UNKNOWN: '#94a3b8',
};

const congestionColors: Record<string, string> = {
  UNKNOWN: '#94a3b8',
  FREE_FLOW: '#22c55e',
  LOW: '#3b82f6',
  MODERATE: '#f59e0b',
  HEAVY: '#f97316',
  CONGESTED: '#ef4444',
};

function createCameraIcon(healthStatus: string): L.DivIcon {
  const color = healthColors[healthStatus] || '#94a3b8';
  return L.divIcon({
    className: 'custom-marker-icon',
    html: `<div style="
      width: 16px; height: 16px;
      background: ${color};
      border: 3px solid #fff;
      border-radius: 50%;
      box-shadow: 0 1px 4px rgba(0,0,0,0.3);
      cursor: pointer;
    "></div>`,
    iconSize: [16, 16],
    iconAnchor: [8, 8],
    popupAnchor: [0, -10],
  });
}

const defaultIcon = L.divIcon({
  className: 'default-violation-icon',
  html: `<div style="
    width: 12px; height: 12px;
    background: #f59e0b;
    border: 2px solid #fff;
    border-radius: 2px;
    box-shadow: 0 1px 3px rgba(0,0,0,0.3);
  "></div>`,
  iconSize: [12, 12],
  iconAnchor: [6, 6],
});

const alertIcon = L.divIcon({
  className: 'default-alert-icon',
  html: `<div style="
    width: 14px; height: 14px;
    background: #ef4444;
    border: 2px solid #fff;
    border-radius: 50%;
    box-shadow: 0 0 6px rgba(239,68,68,0.5);
    cursor: pointer;
  "></div>`,
  iconSize: [14, 14],
  iconAnchor: [7, 7],
});

function formatRelativeTime(iso: string | null): string {
  if (!iso) return '-';
  const diff = Date.now() - new Date(iso).getTime();
  const sec = Math.floor(diff / 1000);
  if (sec < 60) return `${sec}s ago`;
  const min = Math.floor(sec / 60);
  if (min < 60) return `${min}m ago`;
  const hr = Math.floor(min / 60);
  if (hr < 24) return `${hr}h ago`;
  return `${Math.floor(hr / 24)}d ago`;
}

function FitBoundsOnData({ cameras }: { cameras: MapCamera[] }) {
  const map = useMap();
  React.useEffect(() => {
    if (cameras.length > 0) {
      const bounds = L.latLngBounds(cameras.map(c => [c.latitude, c.longitude] as L.LatLngTuple));
      if (bounds.isValid()) {
        map.fitBounds(bounds, { padding: [50, 50], maxZoom: 14 });
      }
    }
  }, [cameras, map]);
  return null;
}

const MapPage: React.FC = () => {
  const navigate = useNavigate();
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [selectedCamera, setSelectedCamera] = useState<MapCamera | null>(null);
  const [healthFilter, setHealthFilter] = useState<string>('ALL');
  const [showViolations, setShowViolations] = useState(true);
  const [showAlerts, setShowAlerts] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [timeRange, setTimeRange] = useState(15);

  const { data, isLoading, error } = useQuery({
    queryKey: ['traffic-map', timeRange],
    queryFn: () => trafficMapService.getTrafficMap(timeRange),
    refetchInterval: 30000,
  });

  const summary = data?.summary;
  const cameras = data?.cameras || [];
  const violations = data?.violations || [];
  const alerts = data?.alerts || [];

  const filteredCameras = cameras.filter(c => {
    if (healthFilter !== 'ALL' && c.healthStatus !== healthFilter) return false;
    if (searchQuery) {
      const q = searchQuery.toLowerCase();
      if (!c.name.toLowerCase().includes(q) && !c.cameraCode.toLowerCase().includes(q) && !(c.location || '').toLowerCase().includes(q)) return false;
    }
    return true;
  });

  const handleCameraClick = useCallback((camera: MapCamera) => {
    setSelectedCamera(camera);
  }, []);

  const center: [number, number] = cameras.length > 0
    ? [cameras[0].latitude, cameras[0].longitude]
    : [27.7172, 85.3240];

  const CameraPopup: React.FC<{ camera: MapCamera }> = ({ camera }) => (
    <div className="map-popup-content">
      <strong style={{ fontSize: 15 }}>{camera.name}</strong>
      <div style={{ marginTop: 4 }}>
        <span className={`status-badge status-${camera.healthStatus.toLowerCase()}`}>
          {camera.healthStatus}
        </span>
      </div>
      <div className="popup-stats">
        <div className="popup-stat">
          <span className="popup-stat-value">{camera.vehiclesLast15Min}</span>
          <span className="popup-stat-label">Vehicles</span>
        </div>
        <div className="popup-stat">
          <span className="popup-stat-value">
            {camera.speedAvailable ? `${camera.averageSpeedKmh!.toFixed(0)}` : '-'}
          </span>
          <span className="popup-stat-label">Avg km/h</span>
        </div>
        <div className="popup-stat">
          <span className="popup-stat-value" style={{
            color: congestionColors[camera.congestionLevel] || '#94a3b8'
          }}>
            {camera.congestionLevel.replace('_', ' ')}
          </span>
          <span className="popup-stat-label">Congestion</span>
        </div>
      </div>
      <hr style={{ margin: '8px 0', border: 'none', borderTop: '1px solid #e2e8f0' }} />
      <div style={{ fontSize: 12, lineHeight: 1.8 }}>
        <div><strong>Code:</strong> {camera.cameraCode}</div>
        <div><strong>Location:</strong> {camera.location || '-'}</div>
        <div><strong>Violations:</strong> {camera.recentViolations} (15m)</div>
        <div><strong>Alerts:</strong> {camera.recentAlerts}</div>
        <div><strong>Last heartbeat:</strong> {formatRelativeTime(camera.lastHeartbeat)}</div>
      </div>
      <button
        className="btn-primary btn-sm"
        style={{ marginTop: 8, width: '100%' }}
        onClick={() => navigate(`/cameras/${camera.id}`)}
      >
        View Camera Details
      </button>
    </div>
  );

  const ViolationPopup: React.FC<{ violation: MapViolation }> = ({ violation }) => (
    <div className="map-popup-content">
      <strong>Violation</strong>
      <div style={{ marginTop: 4 }}>
        <span className="status-badge status-detected">{violation.violationType}</span>
      </div>
      <hr style={{ margin: '8px 0', border: 'none', borderTop: '1px solid #e2e8f0' }} />
      <div style={{ fontSize: 12, lineHeight: 1.8 }}>
        <div><strong>Camera:</strong> {violation.cameraName || violation.cameraId}</div>
        <div><strong>Vehicle:</strong> {violation.vehicleCategory || '-'}</div>
        <div><strong>Plate:</strong> {violation.plateText || '-'}</div>
        <div><strong>Speed:</strong> {violation.speedKmh ? `${violation.speedKmh} km/h` : '-'}</div>
        <div><strong>Confidence:</strong> {violation.confidence ? `${(violation.confidence * 100).toFixed(0)}%` : '-'}</div>
        <div><strong>Time:</strong> {formatRelativeTime(violation.timestamp)}</div>
      </div>
      <button
        className="btn-primary btn-sm"
        style={{ marginTop: 8, width: '100%' }}
        onClick={() => navigate(`/violations/${violation.id}`)}
      >
        View Violation
      </button>
    </div>
  );

  const AlertPopup: React.FC<{ alert: MapAlert }> = ({ alert }) => (
    <div className="map-popup-content">
      <strong>Alert</strong>
      <div style={{ marginTop: 4 }}>
        <span className={`severity-badge severity-${alert.severity.toLowerCase()}`}>
          {alert.severity}
        </span>
      </div>
      <hr style={{ margin: '8px 0', border: 'none', borderTop: '1px solid #e2e8f0' }} />
      <div style={{ fontSize: 12, lineHeight: 1.8 }}>
        <div><strong>Type:</strong> {alert.type}</div>
        <div><strong>Camera:</strong> {alert.cameraName || alert.cameraId || '-'}</div>
        <div><strong>Message:</strong> {alert.message}</div>
        <div><strong>Acknowledged:</strong> {alert.acknowledged ? 'Yes' : 'No'}</div>
        <div><strong>Time:</strong> {formatRelativeTime(alert.createdAt)}</div>
      </div>
    </div>
  );

  if (error) {
    return (
      <div className="map-page">
        <div className="page-header">
          <h2>Traffic Intelligence Map</h2>
        </div>
        <div className="error-message">Failed to load traffic map data</div>
      </div>
    );
  }

  return (
    <div className="map-page intelligence-map">
      <div className="page-header">
        <h2>Traffic Intelligence Map</h2>
        <div className="map-controls">
          <select
            value={timeRange}
            onChange={e => setTimeRange(Number(e.target.value))}
            style={{ fontSize: 13, padding: '4px 8px' }}
          >
            <option value={5}>Last 5 min</option>
            <option value={15}>Last 15 min</option>
            <option value={30}>Last 30 min</option>
            <option value={60}>Last 1 hour</option>
          </select>
          <button
            className={`btn-sm ${sidebarOpen ? 'btn-active' : 'btn-outline'}`}
            onClick={() => setSidebarOpen(v => !v)}
          >
            {sidebarOpen ? 'Hide Panel' : 'Show Panel'}
          </button>
        </div>
      </div>

      <div className="map-body">
        <div className={`map-sidebar ${sidebarOpen ? 'open' : 'closed'}`}>
          <div className="sidebar-section">
            <h3>Summary</h3>
            {isLoading ? (
              <div className="loading" style={{ padding: 20 }}>Loading...</div>
            ) : summary ? (
              <div className="summary-grid">
                <div className="summary-item">
                  <span className="summary-value">{summary.onlineCameras}</span>
                  <span className="summary-label">Online</span>
                </div>
                <div className="summary-item">
                  <span className="summary-value" style={{ color: '#ef4444' }}>{summary.offlineCameras}</span>
                  <span className="summary-label">Offline</span>
                </div>
                <div className="summary-item">
                  <span className="summary-value">{summary.activeStreams}</span>
                  <span className="summary-label">Streams</span>
                </div>
                <div className="summary-item">
                  <span className="summary-value">{summary.currentActiveVehicles}</span>
                  <span className="summary-label">Vehicles</span>
                </div>
                <div className="summary-item">
                  <span className="summary-value" style={{ color: '#f59e0b' }}>{summary.activeViolations}</span>
                  <span className="summary-label">Violations</span>
                </div>
                <div className="summary-item">
                  <span className="summary-value" style={{ color: '#ef4444' }}>{summary.unacknowledgedAlerts}</span>
                  <span className="summary-label">Alerts</span>
                </div>
              </div>
            ) : null}
          </div>

          <div className="sidebar-section">
            <h3>Filters</h3>
            <input
              type="text"
              placeholder="Search cameras..."
              value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
              className="map-search-input"
            />
            <div className="filter-row">
              <select
                value={healthFilter}
                onChange={e => setHealthFilter(e.target.value)}
                className="map-filter-select"
              >
                <option value="ALL">All Status</option>
                <option value="ONLINE">Online</option>
                <option value="OFFLINE">Offline</option>
                <option value="DEGRADED">Degraded</option>
                <option value="UNKNOWN">Unknown</option>
              </select>
            </div>
            <div className="filter-toggles">
              <label className="toggle-label">
                <input type="checkbox" checked={showViolations} onChange={e => setShowViolations(e.target.checked)} />
                Violations
              </label>
              <label className="toggle-label">
                <input type="checkbox" checked={showAlerts} onChange={e => setShowAlerts(e.target.checked)} />
                Alerts
              </label>
            </div>
          </div>

          <div className="sidebar-section cameras-list-section">
            <h3>Cameras ({filteredCameras.length})</h3>
            <div className="cameras-list">
              {filteredCameras.map(cam => (
                <div
                  key={cam.id}
                  className={`camera-list-item ${selectedCamera?.id === cam.id ? 'selected' : ''}`}
                  onClick={() => {
                    setSelectedCamera(cam);
                    handleCameraClick(cam);
                  }}
                >
                  <div className="camera-list-dot" style={{ background: healthColors[cam.healthStatus] || '#94a3b8' }} />
                  <div className="camera-list-info">
                    <div className="camera-list-name">{cam.name}</div>
                    <div className="camera-list-meta">
                      {cam.vehiclesLast15Min} veh · {cam.congestionLevel.replace('_', ' ')}
                    </div>
                  </div>
                  <span className={`status-badge status-${cam.healthStatus.toLowerCase()}`}>
                    {cam.healthStatus}
                  </span>
                </div>
              ))}
            </div>
          </div>
        </div>

        <div className="map-container">
          {isLoading && (
            <div className="map-loading-overlay">
              <div className="loading">Loading traffic data...</div>
            </div>
          )}
          <MapContainer
            center={center}
            zoom={12}
            className="leaflet-map"
            zoomControl={true}
          >
            <TileLayer
              attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
              url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            />
            <FitBoundsOnData cameras={cameras} />

            {filteredCameras.map(camera => (
              <Marker
                key={camera.id}
                position={[camera.latitude, camera.longitude]}
                icon={createCameraIcon(camera.healthStatus)}
                eventHandlers={{
                  click: () => handleCameraClick(camera),
                }}
              >
                <Popup maxWidth={320} minWidth={260}>
                  <CameraPopup camera={camera} />
                </Popup>
              </Marker>
            ))}

            {showViolations && violations.filter(v => v.latitude && v.longitude).map(violation => (
              <Marker
                key={violation.id}
                position={[violation.latitude!, violation.longitude!]}
                icon={defaultIcon}
              >
                <Popup maxWidth={300}>
                  <ViolationPopup violation={violation} />
                </Popup>
              </Marker>
            ))}

            {showAlerts && alerts.filter(a => a.latitude && a.longitude && !a.acknowledged).map(alert => (
              <Marker
                key={alert.id}
                position={[alert.latitude!, alert.longitude!]}
                icon={alertIcon}
              >
                <Popup maxWidth={300}>
                  <AlertPopup alert={alert} />
                </Popup>
              </Marker>
            ))}
          </MapContainer>
        </div>
      </div>
    </div>
  );
};

export default MapPage;
