import React, { createContext, useContext, useState, useEffect, useCallback, useRef, ReactNode } from 'react';
import { cameraService } from '../services/cameraService';
import { Camera, StreamStatus } from '../types';

type LayoutType = '1' | '2' | '4' | 'auto';

interface MonitoringState {
  cameras: Camera[];
  selectedIds: string[];
  layout: LayoutType;
  streams: Record<string, StreamStatus['stream'] | null>;
  loading: boolean;
  error: string | null;
  bulkStarting: boolean;
}

interface MonitoringContextType extends MonitoringState {
  toggleCamera: (id: string) => void;
  selectAll: () => void;
  clearAll: () => void;
  setLayout: (l: LayoutType) => void;
  startAll: () => Promise<void>;
  stopAll: () => Promise<void>;
  refreshCameras: () => Promise<void>;
  addCamera: (cameraId: string) => void;
}

const MonitoringContext = createContext<MonitoringContextType | null>(null);

const LAYOUT_KEY = 'roadeye_live_layout';
const SELECTED_KEY = 'roadeye_live_selected';

function loadFromStorage<T>(key: string, fallback: T): T {
  try {
    const stored = localStorage.getItem(key);
    if (!stored) return fallback;
    const parsed = JSON.parse(stored);
    if (Array.isArray(fallback)) {
      const arr = Array.isArray(parsed) ? parsed : [];
      return [...new Set(arr)] as T;
    }
    return parsed as T;
  } catch { return fallback; }
}

export const MonitoringProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [cameras, setCameras] = useState<Camera[]>(() => []);
  const [selectedIds, setSelectedIds] = useState<string[]>(() =>
    loadFromStorage<string[]>(SELECTED_KEY, [])
  );
  const [layout, setLayoutState] = useState<LayoutType>(() =>
    loadFromStorage<LayoutType>(LAYOUT_KEY, '1')
  );
  const [streams, setStreams] = useState<Record<string, StreamStatus['stream'] | null>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [bulkStarting, setBulkStarting] = useState(false);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const mountedRef = useRef(true);
  const selectedIdsRef = useRef(selectedIds);
  selectedIdsRef.current = selectedIds;

  const loadCameras = useCallback(async () => {
    try {
      const res = await cameraService.getAll({ size: 100 });
      const activeCameras = res.content.filter(c => c.active);
      const activeIds = new Set(activeCameras.map(c => c.id));
      setCameras(activeCameras);

      // Sanitize stale selections
      setSelectedIds(prev => {
        const valid = prev.filter(id => activeIds.has(id));
        const unique = [...new Set(valid)];
        localStorage.setItem(SELECTED_KEY, JSON.stringify(unique));
        return unique;
      });

      setError(null);
    } catch {
      setError('Failed to load cameras');
    } finally {
      setLoading(false);
    }
  }, []);

  // Initial load and periodic camera list refresh
  useEffect(() => {
    mountedRef.current = true;
    loadCameras();
    const refreshInterval = setInterval(loadCameras, 30000);
    return () => {
      mountedRef.current = false;
      clearInterval(refreshInterval);
    };
  }, [loadCameras]);

  // Status polling for selected cameras — uses ref to avoid effect re-creation
  useEffect(() => {
    const poll = async () => {
      if (!mountedRef.current) return;
      const currentIds = selectedIdsRef.current;
      if (currentIds.length === 0) {
        setStreams({});
        return;
      }

      const results: Record<string, StreamStatus['stream'] | null> = {};
      await Promise.all(currentIds.map(async (id) => {
        try {
          const res = await cameraService.getStreamStatus(id);
          results[id] = res.stream;
        } catch {
          results[id] = null;
        }
      }));

      if (mountedRef.current) {
        setStreams(prev => ({ ...prev, ...results }));
      }
    };

    // Poll immediately, then every 3 seconds (fast enough for status changes)
    poll();
    pollRef.current = setInterval(poll, 3000);

    return () => {
      if (pollRef.current) {
        clearInterval(pollRef.current);
        pollRef.current = null;
      }
    };
  }, []); // Run once on mount, never restart — uses ref for current IDs

  const toggleCamera = useCallback((id: string) => {
    setSelectedIds(prev => {
      const next = prev.includes(id) ? prev.filter(x => x !== id) : [...prev, id];
      const unique = [...new Set(next)];
      localStorage.setItem(SELECTED_KEY, JSON.stringify(unique));
      return unique;
    });
  }, []);

  const selectAll = useCallback(() => {
    const ids = cameras.map(c => c.id);
    setSelectedIds(ids);
    localStorage.setItem(SELECTED_KEY, JSON.stringify(ids));
  }, [cameras]);

  const clearAll = useCallback(() => {
    setSelectedIds([]);
    localStorage.setItem(SELECTED_KEY, JSON.stringify([]));
  }, []);

  const setLayout = useCallback((l: LayoutType) => {
    setLayoutState(l);
    localStorage.setItem(LAYOUT_KEY, l);
  }, []);

  const addCamera = useCallback((cameraId: string) => {
    setSelectedIds(prev => {
      if (prev.includes(cameraId)) return prev;
      const next = [...prev, cameraId];
      localStorage.setItem(SELECTED_KEY, JSON.stringify(next));
      return next;
    });
  }, []);

  const startAll = useCallback(async () => {
    setBulkStarting(true);
    const currentSelected = selectedIdsRef.current;
    const currentCameras = cameras;
    const currentStreams = streams;
    const uniqueIds = [...new Set(currentSelected.filter(id => currentCameras.some(c => c.id === id)))];
    try {
      for (const id of uniqueIds) {
        if (currentStreams[id]?.status === 'RUNNING') continue;
        try {
          await cameraService.startStream(id);
        } catch { /* per-card error handles UI */ }
      }
      // Polling will pick up status changes — no manual delay needed
    } finally {
      setBulkStarting(false);
    }
  }, [cameras, streams]);

  const stopAll = useCallback(async () => {
    const currentSelected = selectedIdsRef.current;
    const currentCameras = cameras;
    const uniqueIds = [...new Set(currentSelected.filter(id => currentCameras.some(c => c.id === id)))];
    for (const id of uniqueIds) {
      try {
        await cameraService.stopStream(id);
      } catch { /* ignore */ }
    }
    setStreams(prev => {
      const next = { ...prev };
      uniqueIds.forEach(id => { next[id] = null; });
      return next;
    });
  }, [cameras]);

  return (
    <MonitoringContext.Provider value={{
      cameras, selectedIds, layout, streams, loading, error, bulkStarting,
      toggleCamera, selectAll, clearAll, setLayout, startAll, stopAll,
      refreshCameras: loadCameras, addCamera,
    }}>
      {children}
    </MonitoringContext.Provider>
  );
};

export const useMonitoring = (): MonitoringContextType => {
  const context = useContext(MonitoringContext);
  if (!context) {
    throw new Error('useMonitoring must be used within a MonitoringProvider');
  }
  return context;
};
