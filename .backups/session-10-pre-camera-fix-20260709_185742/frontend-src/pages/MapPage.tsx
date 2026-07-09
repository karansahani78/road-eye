import React, { useState, useCallback, useRef, useEffect } from 'react';
import {
  MapContainer, TileLayer, Marker, Popup, Tooltip, useMap
} from 'react-leaflet';
import { useQuery } from '@tanstack/react-query';
import L from 'leaflet';
import { trafficMapService, MapCamera, MapViolation, MapAlert } from '../services/trafficMapService';
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

const streamColors: Record<string, string> = {
  RUNNING: '#3b82f6',
  STOPPED: '#94a3b8',
  ERROR: '#ef4444',
};

function createCameraIcon(healthStatus: string, streamStatus: string, selected: boolean): L.DivIcon {
  const baseColor = healthColors[healthStatus] || '#94a3b8';
  const ringColor = streamStatus === 'RUNNING' ? '#3b82f6' : baseColor;
  const size = selected ? 18 : 14;
  const ringWidth = selected ? 3 : 2;
  return L.divIcon({
    className: 'custom-marker-icon',
    html: `<div style="
      width: ${size}px; height: ${size}px;
      background: ${baseColor};
      border: ${ringWidth}px solid ${ringColor};
      border-radius: 50%;
      box-shadow: 0 1px 4px rgba(0,0,0,0.3);
      cursor: pointer;
      transition: all 0.15s;
    "></div>`,
    iconSize: [size + ringWidth * 2, size + ringWidth * 2],
    iconAnchor: [size / 2 + ringWidth, size / 2 + ringWidth],
    popupAnchor: [0, -(size / 2 + ringWidth + 4)],
  });
}

const violationIcon = L.divIcon({
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
  if (!iso) return 'N/A';
  const diff = Date.now() - new Date(iso).getTime();
  const sec = Math.floor(diff / 1000);
  if (sec < 60) return `${sec}s ago`;
  const min = Math.floor(sec / 60);
  if (min < 60) return `${min}m ago`;
  const hr = Math.floor(min / 60);
  if (hr < 24) return `${hr}h ago`;
  return `${Math.floor(hr / 24)}d ago`;
}

function formatCongestion(level: string): string {
  return level.replace('_', ' ');
}

function formatLocation(camera: MapCamera): string {
  if (camera.location) return camera.location;
  if (camera.latitude != null && camera.longitude != null) {
    return `${camera.latitude.toFixed(4)}, ${camera.longitude.toFixed(4)}`;
  }
  return 'Not Configured';
}

function MapResizer() {
  const map = useMap();
  const initialized = useRef(false);
  useEffect(() => {
    if (!initialized.current) {
      initialized.current = true;
      setTimeout(() => map.invalidateSize(), 100);
    }
  }, [map]);
  return null;
}

function MapControlsInner({ cameras, selectedCameraId, onReady }: {
  cameras: MapCamera[];
  selectedCameraId: string | null;
  onReady: (map: L.Map) => void;
}) {
  const map = useMap();
  const initialFit = useRef(false);
  const prevSelected = useRef<string | null>(null);

  useEffect(() => {
    onReady(map);
  }, [map, onReady]);

  useEffect(() => {
    if (cameras.length > 0 && !initialFit.current) {
      initialFit.current = true;
      const bounds = L.latLngBounds(cameras.map(c => [c.latitude, c.longitude] as L.LatLngTuple));
      if (bounds.isValid()) {
        map.fitBounds(bounds, { padding: [50, 50], maxZoom: 14 });
      }
    }
  }, [cameras, map]);

  useEffect(() => {
    if (selectedCameraId && selectedCameraId !== prevSelected.current) {
      prevSelected.current = selectedCameraId;
      const cam = cameras.find(c => c.id === selectedCameraId);
      if (cam) {
        map.setView([cam.latitude, cam.longitude], 15, { animate: true });
      }
    }
  }, [selectedCameraId, cameras, map]);

  const doFitCameras = () => {
    if (cameras.length > 0) {
      const bounds = L.latLngBounds(cameras.map(c => [c.latitude, c.longitude] as L.LatLngTuple));
      if (bounds.isValid()) {
        map.fitBounds(bounds, { padding: [50, 50], maxZoom: 14 });
      }
    }
  };

  const doFullscreen = () => {
    const el = document.querySelector('.intelligence-map .map-body') as HTMLElement;
    if (!el) return;
    if (document.fullscreenElement) {
      document.exitFullscreen();
    } else {
      el.requestFullscreen();
    }
  };

  (map as any).__fitCameras = doFitCameras;
  (map as any).__toggleFullscreen = doFullscreen;

  return null;
}

const MapPage: React.FC = () => {
  const navigate = useNavigate();
  const [sidebarOpen, setSidebarOpen] = useState(true);
  const [selectedCameraId, setSelectedCameraId] = useState<string | null>(null);
  const [healthFilter, setHealthFilter] = useState<string>('ALL');
  const [showViolations, setShowViolations] = useState(true);
  const [showAlerts, setShowAlerts] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [timeRange, setTimeRange] = useState(15);
  const [baseLayer, setBaseLayer] = useState<'street' | 'satellite'>('street');
  const mapRef = useRef<L.Map | null>(null);
  const handleMapReady = useCallback((m: L.Map) => { mapRef.current = m; }, []);

  const { data, isLoading, error, refetch } = useQuery({
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
      if (!c.name.toLowerCase().includes(q) &&
          !c.cameraCode.toLowerCase().includes(q) &&
          !(c.location || '').toLowerCase().includes(q)) return false;
    }
    return true;
  });

  const center: [number, number] = cameras.length > 0
    ? [cameras[0].latitude, cameras[0].longitude]
    : [27.7172, 85.3240];

  const CameraPopup: React.FC<{ camera: MapCamera }> = ({ camera }) => (
    <div className="map-popup-content">
      <strong style={{ fontSize: 15 }}>{camera.name}</strong>
      <div style={{ marginTop: 4, display: 'flex', gap: 4, alignItems: 'center' }}>
        <span className={`status-badge status-${camera.healthStatus.toLowerCase()}`}>
          {camera.healthStatus}
        </span>
        <span style={{
          fontSize: 10, color: streamColors[camera.streamStatus] || '#94a3b8',
          fontWeight: 600, textTransform: 'uppercase'
        }}>
          {camera.streamStatus === 'RUNNING' ? 'Streaming' : 'Stopped'}
        </span>
      </div>
      <div className="popup-stats">
        <div className="popup-stat">
          <span className="popup-stat-value">{camera.vehiclesLast15Min}</span>
          <span className="popup-stat-label">Vehicles</span>
        </div>
        <div className="popup-stat">
          <span className="popup-stat-value">
            {camera.speedAvailable ? `${camera.averageSpeedKmh!.toFixed(0)}` : 'N/A'}
          </span>
          <span className="popup-stat-label">km/h</span>
        </div>
        <div className="popup-stat">
          <span className="popup-stat-value" style={{
            color: congestionColors[camera.congestionLevel] || '#94a3b8'
          }}>
            {formatCongestion(camera.congestionLevel)}
          </span>
          <span className="popup-stat-label">Traffic</span>
        </div>
      </div>
      <hr style={{ margin: '8px 0', border: 'none', borderTop: '1px solid #e2e8f0' }} />
      <div style={{ fontSize: 12, lineHeight: 1.8 }}>
        <div><strong>Code:</strong> {camera.cameraCode}</div>
        <div><strong>Location:</strong> {formatLocation(camera)}</div>
        <div><strong>Violations:</strong> {camera.recentViolations}</div>
        <div><strong>Alerts:</strong> {camera.recentAlerts}</div>
        <div><strong>Last Heartbeat:</strong> {formatRelativeTime(camera.lastHeartbeat)}</div>
      </div>
      <div className="popup-actions">
        <button
          className="btn-primary btn-sm"
          onClick={() => navigate(`/cameras/${camera.id}`)}
        >
          Camera Details
        </button>
        <button
          className="btn-secondary btn-sm"
          onClick={() => navigate(`/live?cameraId=${camera.id}`)}
        >
          Live Monitor
        </button>
      </div>
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
        <div><strong>Vehicle:</strong> {violation.vehicleCategory || 'N/A'}</div>
        <div><strong>Plate:</strong> {violation.plateText || 'N/A'}</div>
        <div><strong>Speed:</strong> {violation.speedKmh ? `${violation.speedKmh} km/h` : 'N/A'}</div>
        <div><strong>Confidence:</strong> {violation.confidence ? `${(violation.confidence * 100).toFixed(0)}%` : 'N/A'}</div>
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
        <div><strong>Camera:</strong> {alert.cameraName || alert.cameraId || 'N/A'}</div>
        <div><strong>Message:</strong> {alert.message}</div>
        <div><strong>Acknowledged:</strong> {alert.acknowledged ? 'Yes' : 'No'}</div>
        <div><strong>Time:</strong> {formatRelativeTime(alert.createdAt)}</div>
      </div>
    </div>
  );

  if (error) {
    return (
      <div className="map-page intelligence-map" style={{ padding: 40 }}>
        <h2>Traffic Intelligence Map</h2>
        <div className="error-message" style={{ marginTop: 16 }}>
          Failed to load traffic map data
        </div>
      </div>
    );
  }

  return (
    <div className="map-page intelligence-map">
      <div className="page-header">
        <h2>Traffic Intelligence</h2>
        <div className="map-controls">
          <input
            type="text"
            placeholder="Search cameras..."
            value={searchQuery}
            onChange={e => setSearchQuery(e.target.value)}
            style={{
              fontSize: 13, padding: '4px 8px', width: 180,
              border: '1px solid var(--border)', borderRadius: 6
            }}
          />
          <select
            value={timeRange}
            onChange={e => setTimeRange(Number(e.target.value))}
            style={{ fontSize: 13, padding: '4px 8px' }}
          >
            <option value={5}>5 min</option>
            <option value={15}>15 min</option>
            <option value={30}>30 min</option>
            <option value={60}>1 hr</option>
            <option value={360}>6 hr</option>
            <option value={1440}>24 hr</option>
          </select>
          <button
            className="btn-sm btn-outline"
            onClick={() => refetch()}
            title="Refresh data"
          >
            ↻
          </button>
          <button
            className={`btn-sm ${baseLayer === 'street' ? 'btn-active' : 'btn-outline'}`}
            onClick={() => setBaseLayer('street')}
            title="Street map"
          >
            Street
          </button>
          <button
            className={`btn-sm ${baseLayer === 'satellite' ? 'btn-active' : 'btn-outline'}`}
            onClick={() => setBaseLayer('satellite')}
            title="Satellite imagery"
          >
            Satellite
          </button>
          <button
            className={`btn-sm ${sidebarOpen ? 'btn-active' : 'btn-outline'}`}
            onClick={() => setSidebarOpen(v => !v)}
          >
            {sidebarOpen ? 'Hide Panel' : 'Panel'}
          </button>
        </div>
      </div>

      <div className="map-body">
        <div className={`map-sidebar ${sidebarOpen ? 'open' : 'closed'}`}>
          <div className="sidebar-section">
            <h3>Summary</h3>
            {isLoading ? (
              <div className="loading" style={{ padding: '10px 0' }}>Loading...</div>
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
                  <span className="summary-value" style={{ color: '#3b82f6' }}>{summary.activeStreams}</span>
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
              {filteredCameras.length === 0 && !isLoading && (
                <div style={{ fontSize: 12, color: 'var(--text-muted)', padding: '8px 0' }}>
                  {searchQuery || healthFilter !== 'ALL'
                    ? 'No cameras match filters'
                    : 'No cameras available'}
                </div>
              )}
              {filteredCameras.map(cam => (
                <div
                  key={cam.id}
                  className={`camera-list-item ${selectedCameraId === cam.id ? 'selected' : ''}`}
                  onClick={() => {
                    setSelectedCameraId(cam.id);
                  }}
                >
                  <div
                    className="camera-list-dot"
                    style={{
                      background: healthColors[cam.healthStatus] || '#94a3b8',
                      boxShadow: cam.streamStatus === 'RUNNING'
                        ? '0 0 0 2px #3b82f6'
                        : 'none'
                    }}
                  />
                  <div className="camera-list-info">
                    <div className="camera-list-name">{cam.name}</div>
                    <div className="camera-list-meta">
                      {cam.streamStatus === 'RUNNING' ? 'STREAMING' : 'STOPPED'}
                      {cam.vehiclesLast15Min > 0 ? ` · ${cam.vehiclesLast15Min} veh` : ''}
                      {cam.congestionLevel !== 'UNKNOWN' ? ` · ${formatCongestion(cam.congestionLevel)}` : ''}
                    </div>
                  </div>
                  <span className={`status-badge status-${cam.healthStatus.toLowerCase()}`}>
                    {cam.healthStatus === 'ONLINE' ? 'ON' : cam.healthStatus === 'OFFLINE' ? 'OFF' : cam.healthStatus}
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
            zoom={13}
            style={{ width: '100%', height: '100%', minHeight: 400 }}
            zoomControl={true}
          >
            {baseLayer === 'street' ? (
              <TileLayer
                attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
                url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                maxZoom={19}
              />
            ) : (
              <TileLayer
                attribution='&copy; <a href="https://www.esri.com/">Esri</a>'
                url="https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
                maxZoom={18}
              />
            )}

            <MapResizer />
            <MapControlsInner
              cameras={filteredCameras}
              selectedCameraId={selectedCameraId}
              onReady={handleMapReady}
            />

            {filteredCameras.map(camera => (
              <Marker
                key={camera.id}
                position={[camera.latitude, camera.longitude]}
                icon={createCameraIcon(camera.healthStatus, camera.streamStatus, selectedCameraId === camera.id)}
                eventHandlers={{
                  click: () => setSelectedCameraId(camera.id),
                }}
              >
                <Tooltip direction="top" offset={[0, -10]} opacity={0.95}>
                  <strong>{camera.name}</strong>
                  <br />
                  <span style={{ fontSize: 11 }}>
                    {camera.healthStatus}
                    {camera.streamStatus === 'RUNNING' ? ' · Streaming' : ''}
                  </span>
                </Tooltip>
                <Popup maxWidth={320} minWidth={260}>
                  <CameraPopup camera={camera} />
                </Popup>
              </Marker>
            ))}

            {showViolations && violations.filter(v => v.latitude && v.longitude).map(violation => (
              <Marker
                key={violation.id}
                position={[violation.latitude!, violation.longitude!]}
                icon={violationIcon}
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

          <div className="map-overlay-controls">
            <button
              className="map-control-btn"
              onClick={() => {
                if (mapRef.current && (mapRef.current as any).__fitCameras) {
                  (mapRef.current as any).__fitCameras();
                }
              }}
              title="Fit all cameras in view"
            >
              ⊞ Fit
            </button>
            <button
              className="map-control-btn"
              onClick={() => {
                const el = document.querySelector('.intelligence-map .map-body') as HTMLElement;
                if (!el) return;
                if (document.fullscreenElement) {
                  document.exitFullscreen();
                  setTimeout(() => mapRef.current?.invalidateSize(), 100);
                } else {
                  el.requestFullscreen();
                  setTimeout(() => mapRef.current?.invalidateSize(), 100);
                }
              }}
              title="Toggle fullscreen"
            >
              ⛶ Full
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default MapPage;
