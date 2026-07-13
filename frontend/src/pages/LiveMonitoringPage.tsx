import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useMonitoring } from '../hooks/useMonitoring';
import { StreamStatus } from '../types';

const LiveMonitoringPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const {
    cameras, selectedIds, layout, streams, loading, error, bulkStarting,
    toggleCamera, selectAll, clearAll, setLayout, startAll, stopAll, addCamera,
  } = useMonitoring();

  // Auto-select camera from URL query param (e.g. from Traffic Map)
  useEffect(() => {
    const cameraId = searchParams.get('cameraId');
    if (cameraId && cameras.length > 0) {
      const cameraExists = cameras.some(c => c.id === cameraId);
      if (cameraExists) {
        addCamera(cameraId);
      }
    }
  }, [searchParams, cameras, addCamera]);

  const gridCols = layout === '1' ? 1 : layout === '2' ? 2 : layout === '4' ? 2 : 0;
  const gridClass = layout === 'auto' ? 'auto-grid' : `cols-${gridCols}`;

  return (
    <div className="live-monitoring">
      <div className="page-header">
        <h1>Live Monitoring</h1>
        <div className="toolbar">
          <div className="layout-buttons">
            {(['1', '2', '4', 'auto'] as const).map(l => (
              <button key={l} className={`btn btn-sm ${layout === l ? 'btn-active' : 'btn-outline'}`}
                onClick={() => setLayout(l)}>
                {l === '1' ? '1' : l === '2' ? '2' : l === '4' ? '4' : 'Auto'}
              </button>
            ))}
          </div>
          <div className="stream-actions">
            <button className="btn btn-sm btn-primary" onClick={startAll}
              disabled={selectedIds.length === 0 || bulkStarting}>
              {bulkStarting ? 'Starting...' : 'Start All'}
            </button>
            <button className="btn btn-sm btn-danger" onClick={stopAll}
              disabled={selectedIds.length === 0}>
              Stop All
            </button>
          </div>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      {loading ? (
        <div className="loading">Loading cameras...</div>
      ) : (
        <div className="monitoring-layout">
          <div className="camera-selector-panel">
            <div className="selector-header">
              <h3>Cameras</h3>
              <div className="selector-actions">
                <button className="btn btn-xs" onClick={selectAll}>All</button>
                <button className="btn btn-xs" onClick={clearAll}>None</button>
              </div>
            </div>
            {cameras.length === 0 ? (
              <div className="empty-state">
                <p>No active cameras found.</p>
                <a href="/cameras" className="btn btn-sm btn-primary">Add Camera</a>
              </div>
            ) : (
              <div className="camera-selector-list">
                {cameras.map(cam => (
                  <label key={cam.id} className={`camera-select-item ${selectedIds.includes(cam.id) ? 'selected' : ''}`}>
                    <input type="checkbox" checked={selectedIds.includes(cam.id)}
                      onChange={() => toggleCamera(cam.id)} />
                    <div className="camera-select-info">
                      <span className="camera-name">{cam.name}</span>
                      <span className={`camera-status status-${cam.healthStatus?.toLowerCase() || 'unknown'}`}>
                        {cam.healthStatus || 'UNKNOWN'}
                      </span>
                    </div>
                  </label>
                ))}
              </div>
            )}
          </div>

          <div className="camera-grid-panel">
            {selectedIds.length === 0 ? (
              <div className="empty-view">
                <div className="empty-icon">&#128247;</div>
                <p>Select cameras from the list to start monitoring</p>
                <p className="hint">Or add a new camera to get started</p>
              </div>
            ) : (
              <div className={`camera-grid ${gridClass}`}>
                {selectedIds.map(id => {
                  const cam = cameras.find(c => c.id === id);
                  return (
                    <LiveCameraCard key={id}
                      cameraId={id}
                      cameraName={cam?.name || id.substring(0, 8)}
                      stream={streams[id] || null}
                      bulkStarting={bulkStarting}
                    />
                  );
                })}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
};

const LiveCameraCard: React.FC<{
  cameraId: string;
  cameraName: string;
  stream: StreamStatus['stream'] | null;
  bulkStarting: boolean;
}> = ({ cameraId, cameraName, stream, bulkStarting }) => {
  const [frameSrc, setFrameSrc] = useState<string | null>(null);
  const [zoom, setZoom] = useState(1);
  const [pan, setPan] = useState({ x: 0, y: 0 });
  const [isDragging, setIsDragging] = useState(false);
  const [dragStart, setDragStart] = useState({ x: 0, y: 0 });
  const imgRef = useRef<HTMLImageElement>(null);
  const errorRetryRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const startRequestedRef = useRef(false);

  const streamStatus = stream?.status;
  const streamActive = streamStatus === 'RUNNING';
  const isStarting = streamStatus === 'STARTING' || streamStatus === 'CONNECTING';
  const isError = streamStatus === 'ERROR';

  // Set MJPEG source when stream becomes RUNNING
  useEffect(() => {
    if (streamActive) {
      setFrameSrc(`/api/v1/cameras/${cameraId}/stream/mjpeg?_=${Date.now()}`);
      startRequestedRef.current = false;
    } else if (streamStatus === 'STOPPED' || streamStatus === 'ERROR') {
      setFrameSrc(null);
    }
    return () => {
      if (errorRetryRef.current) clearTimeout(errorRetryRef.current);
    };
  }, [streamActive, streamStatus, cameraId]);

  const handleMjpegError = useCallback(() => {
    if (errorRetryRef.current) clearTimeout(errorRetryRef.current);
    errorRetryRef.current = setTimeout(() => {
      if (streamActive) {
        setFrameSrc(`/api/v1/cameras/${cameraId}/stream/mjpeg?_=${Date.now()}`);
      }
    }, 3000);
  }, [cameraId, streamActive]);

  const handleStart = async () => {
    if (streamActive || isStarting || startRequestedRef.current) return;
    startRequestedRef.current = true;
    try {
      const { cameraService } = await import('../services/cameraService');
      await cameraService.startStream(cameraId);
      // Status will update via polling — no artificial delay needed
    } catch (err: any) {
      startRequestedRef.current = false;
      // Error will be reflected via stream status polling
    }
  };

  const handleStop = async () => {
    startRequestedRef.current = false;
    try {
      const { cameraService } = await import('../services/cameraService');
      await cameraService.stopStream(cameraId);
    } catch { /* status updates via polling */ }
  };

  const handleWheel = (e: React.WheelEvent) => {
    e.preventDefault();
    const delta = e.deltaY > 0 ? -0.1 : 0.1;
    setZoom(z => Math.max(1, Math.min(5, z + delta)));
  };

  const handleMouseDown = (e: React.MouseEvent) => {
    if (zoom > 1) { setIsDragging(true); setDragStart({ x: e.clientX - pan.x, y: e.clientY - pan.y }); }
  };

  const handleMouseMove = (e: React.MouseEvent) => {
    if (isDragging && zoom > 1) setPan({ x: e.clientX - dragStart.x, y: e.clientY - dragStart.y });
  };

  const handleMouseUp = () => setIsDragging(false);
  const resetZoom = () => { setZoom(1); setPan({ x: 0, y: 0 }); };

  const cardState = streamActive ? 'RUNNING' : isStarting ? 'STARTING' : isError ? 'ERROR' : 'STOPPED';

  return (
    <div className={`camera-card state-${cardState.toLowerCase()}`}>
      <div className="camera-card-header">
        <span className="camera-card-name" title={cameraName}>{cameraName}</span>
        <span className={`camera-card-state state-${cardState.toLowerCase()}`}>
          {cardState === 'RUNNING' ? 'LIVE' : cardState === 'STARTING' ? 'Connecting...' : cardState === 'ERROR' ? 'Error' : 'Stopped'}
        </span>
      </div>

      <div className="camera-card-body"
        onWheel={handleWheel}
        onMouseDown={handleMouseDown}
        onMouseMove={handleMouseMove}
        onMouseUp={handleMouseUp}
        onMouseLeave={handleMouseUp}
        style={{ cursor: zoom > 1 ? (isDragging ? 'grabbing' : 'grab') : 'default' }}>
        {streamActive ? (
          frameSrc ? (
            <div className="camera-feed-container" style={{ overflow: 'hidden', width: '100%', height: '100%' }}>
              <img ref={imgRef} src={frameSrc} alt="Live feed"
                className="live-feed"
                style={{
                  transform: `translate(${pan.x}px, ${pan.y}px) scale(${zoom})`,
                  transformOrigin: 'center center',
                  cursor: zoom > 1 ? (isDragging ? 'grabbing' : 'grab') : 'default',
                }}
                onError={handleMjpegError}
                draggable={false} />
            </div>
          ) : (
            <div className="video-placeholder"><div className="spinner" /><p>Waiting for first frame...</p></div>
          )
        ) : (
          <div className="video-placeholder">
            {isStarting ? (
              <><div className="spinner" /><p>Connecting to camera...</p></>
            ) : isError ? (
              <>
                <div className="error-icon">&#9888;</div>
                <p>Stream error</p>
                {stream?.error_message && <p className="hint">{stream.error_message}</p>}
                <button className="btn btn-sm btn-primary" onClick={handleStart}>Retry</button>
              </>
            ) : (
              <>
                <div className="offline-icon">&#128247;</div>
                <p>Stream not active</p>
                <button className="btn btn-sm btn-primary" onClick={handleStart}
                  disabled={isStarting || bulkStarting}>
                  {isStarting || bulkStarting ? 'Starting...' : 'Start Stream'}
                </button>
              </>
            )}
          </div>
        )}
      </div>

      {streamActive && (
        <div className="camera-card-footer">
          <div className="card-controls">
            <button className="btn btn-xs btn-outline" onClick={() => setZoom(z => Math.min(5, z + 0.2))}>+</button>
            <button className="btn btn-xs btn-outline" onClick={() => setZoom(z => Math.max(1, z - 0.2))}>-</button>
            {zoom > 1 && <button className="btn btn-xs btn-outline" onClick={resetZoom}>Reset</button>}
            <button className="btn btn-xs btn-danger" onClick={handleStop}>Stop</button>
          </div>
          <div className="card-stats">
            <span>{stream?.fps?.toFixed(1) || '?'} FPS</span>
            <span>{stream?.vehicles_detected ?? '?'} veh</span>
            <span>F:{stream?.frame_count ?? 0}</span>
            <span>D:{stream?.dropped_frames ?? 0}</span>
          </div>
        </div>
      )}

      {!streamActive && (
        <div className="camera-card-footer">
          {stream?.error_message && <span className="card-error">{stream.error_message}</span>}
        </div>
      )}
    </div>
  );
};

export default LiveMonitoringPage;
