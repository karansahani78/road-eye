import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useSearchParams } from 'react-router-dom';
import { cameraService } from '../services/cameraService';
import { Camera, StreamStatus } from '../types';

type LayoutType = '1' | '2' | '4' | 'auto';

const LAYOUT_KEY = 'roadeye_live_layout';
const SELECTED_KEY = 'roadeye_live_selected';

const LiveMonitoringPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const [cameras, setCameras] = useState<Camera[]>([]);
  const [selectedIds, setSelectedIds] = useState<string[]>(() => {
    try { return JSON.parse(localStorage.getItem(SELECTED_KEY) || '[]'); }
    catch { return []; }
  });
  const [layout, setLayout] = useState<LayoutType>(() => {
    return (localStorage.getItem(LAYOUT_KEY) as LayoutType) || '1';
  });
  const [streams, setStreams] = useState<Record<string, StreamStatus['stream'] | null>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [bulkStarting, setBulkStarting] = useState(false);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    setLoading(true);
    cameraService.getAll({ size: 100 })
      .then(res => {
        setCameras(res.content.filter(c => c.active));
        setLoading(false);
      })
      .catch(() => {
        setError('Failed to load cameras');
        setLoading(false);
      });
  }, []);

  // Auto-select camera from URL query param (e.g. from Traffic Map)
  useEffect(() => {
    const cameraId = searchParams.get('cameraId');
    if (cameraId && cameras.length > 0) {
      const cameraExists = cameras.some(c => c.id === cameraId);
      if (cameraExists && !selectedIds.includes(cameraId)) {
        const updated = [...selectedIds, cameraId];
        setSelectedIds(updated);
        localStorage.setItem(SELECTED_KEY, JSON.stringify(updated));
      }
    }
  }, [searchParams, cameras, selectedIds]);

  // Single centralized status poller
  useEffect(() => {
    if (selectedIds.length === 0) return;
    const poll = async () => {
      const results: Record<string, StreamStatus['stream'] | null> = {};
      await Promise.all(selectedIds.map(async (id) => {
        try {
          const res = await cameraService.getStreamStatus(id);
          results[id] = res.stream;
        } catch { results[id] = null; }
      }));
      setStreams(prev => ({ ...prev, ...results }));
    };
    poll();
    pollRef.current = setInterval(poll, 5000);
    return () => { if (pollRef.current) clearInterval(pollRef.current); };
  }, [selectedIds.join(',')]);

  const persistSelected = useCallback((ids: string[]) => {
    setSelectedIds(ids);
    localStorage.setItem(SELECTED_KEY, JSON.stringify(ids));
  }, []);

  const persistLayout = useCallback((l: LayoutType) => {
    setLayout(l);
    localStorage.setItem(LAYOUT_KEY, l);
  }, []);

  const toggleCamera = (id: string) => {
    persistSelected(
      selectedIds.includes(id) ? selectedIds.filter(x => x !== id) : [...selectedIds, id]
    );
  };

  const selectAll = () => persistSelected(cameras.map(c => c.id));
  const clearAll = () => persistSelected([]);

  const handleStartAll = async () => {
    setBulkStarting(true);
    try {
      for (const id of selectedIds) {
        if (streams[id]?.status === 'RUNNING') continue;
        try {
          await cameraService.startStream(id);
        } catch { /* per-card error state handles UI feedback */ }
      }
      await new Promise(r => setTimeout(r, 2000));
      selectedIds.forEach(async (id) => {
        try {
          const res = await cameraService.getStreamStatus(id);
          setStreams(prev => ({ ...prev, [id]: res.stream }));
        } catch {}
      });
    } finally {
      setBulkStarting(false);
    }
  };

  const handleStopAll = async () => {
    for (const id of selectedIds) {
      try {
        await cameraService.stopStream(id);
      } catch { /* ignore stop errors */ }
    }
    setStreams(prev => {
      const next = { ...prev };
      selectedIds.forEach(id => { next[id] = null; });
      return next;
    });
  };

  const gridCols = layout === '1' ? 1 : layout === '2' ? 2 : layout === '4' ? 2 : 0;
  const gridClass = layout === 'auto'
    ? 'auto-grid'
    : `cols-${gridCols}`;

  return (
    <div className="live-monitoring">
      <div className="page-header">
        <h1>Live Monitoring</h1>
        <div className="toolbar">
          <div className="layout-buttons">
            {(['1', '2', '4', 'auto'] as LayoutType[]).map(l => (
              <button key={l} className={`btn btn-sm ${layout === l ? 'btn-active' : 'btn-outline'}`}
                onClick={() => persistLayout(l)}>
                {l === '1' ? '1' : l === '2' ? '2' : l === '4' ? '4' : 'Auto'}
              </button>
            ))}
          </div>
          <div className="stream-actions">
            <button className="btn btn-sm btn-primary" onClick={handleStartAll}
              disabled={selectedIds.length === 0 || bulkStarting}>
              {bulkStarting ? 'Starting...' : 'Start All'}
            </button>
            <button className="btn btn-sm btn-danger" onClick={handleStopAll}
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
              <p className="empty-state">No active cameras found.</p>
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
                <p>Select cameras from the list to start monitoring.</p>
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
  const [starting, setStarting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [zoom, setZoom] = useState(1);
  const [pan, setPan] = useState({ x: 0, y: 0 });
  const [isDragging, setIsDragging] = useState(false);
  const [dragStart, setDragStart] = useState({ x: 0, y: 0 });
  const imgRef = useRef<HTMLImageElement>(null);
  const errorRetryRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const streamActive = stream?.status === 'RUNNING';

  // Frame polling when stream is active
  useEffect(() => {
    if (streamActive) {
      const mjpegUrl = `/api/v1/cameras/${cameraId}/stream/mjpeg?_=${Date.now()}`;
      setFrameSrc(mjpegUrl);
      setError(null);
    } else {
      setFrameSrc(null);
    }
    return () => {
      if (errorRetryRef.current) clearTimeout(errorRetryRef.current);
    };
  }, [streamActive, cameraId]);

  const handleMjpegError = useCallback(() => {
    // Auto-retry MJPEG connection after 3 seconds if stream is still RUNNING
    setError('Stream disconnected, reconnecting...');
    if (errorRetryRef.current) clearTimeout(errorRetryRef.current);
    errorRetryRef.current = setTimeout(() => {
      if (streamActive) {
        const mjpegUrl = `/api/v1/cameras/${cameraId}/stream/mjpeg?_=${Date.now()}`;
        setFrameSrc(mjpegUrl);
        setError(null);
      }
    }, 3000);
  }, [cameraId, streamActive]);

  const handleStart = async () => {
    if (streamActive || starting) return;
    setStarting(true); setError(null);
    try {
      await cameraService.startStream(cameraId);
      await new Promise(r => setTimeout(r, 2000));
    } catch (err: any) {
      const msg = err?.response?.data?.message || err?.message || 'Failed to start stream';
      setError(msg);
    } finally { setStarting(false); }
  };

  const handleStop = async () => {
    try {
      await cameraService.stopStream(cameraId);
    } catch (err: any) {
      setError(err?.response?.data?.message || 'Failed to stop stream');
    }
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

  const cardState = streamActive ? 'RUNNING' : starting ? 'STARTING' : stream?.status === 'ERROR' ? 'ERROR' : 'STOPPED';

  return (
    <div className={`camera-card state-${cardState.toLowerCase()}`}>
      <div className="camera-card-header">
        <span className="camera-card-name" title={cameraName}>{cameraName}</span>
        <span className={`camera-card-state state-${cardState.toLowerCase()}`}>
          {cardState === 'RUNNING' ? 'LIVE' : cardState === 'STARTING' ? 'Starting...' : cardState === 'ERROR' ? 'Error' : 'Stopped'}
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
            <div className="video-placeholder"><p>Waiting for first frame...</p></div>
          )
        ) : (
          <div className="video-placeholder">
            {cardState === 'STARTING' ? (
              <p>Connecting to camera...</p>
            ) : cardState === 'ERROR' ? (
              <>
                <p>Stream error</p>
                {stream?.error_message && <p className="hint">{stream.error_message}</p>}
                <button className="btn btn-sm btn-primary" onClick={handleStart}>Retry</button>
              </>
            ) : (
              <>
                <p>Stream not active</p>
                <button className="btn btn-sm btn-primary" onClick={handleStart}
                  disabled={starting || bulkStarting}>
                  {starting || bulkStarting ? 'Starting...' : 'Start'}
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
          {error && <span className="card-error">{error}</span>}
        </div>
      )}
    </div>
  );
};

export default LiveMonitoringPage;
