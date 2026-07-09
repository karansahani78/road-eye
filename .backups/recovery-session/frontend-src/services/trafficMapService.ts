import api from './api';
import { ApiResponse } from '../types';

export interface TrafficMapData {
  summary: {
    totalCameras: number;
    mappedCameras: number;
    unmappedCameras: number;
    onlineCameras: number;
    offlineCameras: number;
    degradedCameras: number;
    activeStreams: number;
    currentActiveVehicles: number;
    activeViolations: number;
    unacknowledgedAlerts: number;
  };
  cameras: MapCamera[];
  violations: MapViolation[];
  alerts: MapAlert[];
}

export interface MapCamera {
  id: string;
  cameraCode: string;
  name: string;
  latitude: number;
  longitude: number;
  healthStatus: string;
  streamStatus: string;
  location: string;
  currentFps: number | null;
  vehiclesLast15Min: number;
  averageSpeedKmh: number | null;
  speedAvailable: boolean;
  congestionLevel: string;
  recentViolations: number;
  recentAlerts: number;
  lastHeartbeat: string | null;
  lastObservation: string | null;
}

export interface MapViolation {
  id: string;
  violationType: string;
  cameraId: string;
  cameraName: string;
  latitude: number | null;
  longitude: number | null;
  status: string;
  confidence: number | null;
  vehicleCategory: string;
  plateText: string;
  speedKmh: number | null;
  timestamp: string;
}

export interface MapAlert {
  id: string;
  type: string;
  severity: string;
  cameraId: string;
  cameraName: string;
  latitude: number | null;
  longitude: number | null;
  message: string;
  acknowledged: boolean;
  createdAt: string;
}

export const trafficMapService = {
  getTrafficMap: async (minutes: number = 15): Promise<TrafficMapData> => {
    const response = await api.get<ApiResponse<TrafficMapData>>('/traffic-map', { params: { minutes } });
    return response.data.data;
  },
};
