import api from './api';
import { Camera, CameraCalibration, ApiResponse, PagedResponse, CameraHealthEvent, StreamStatus } from '../types';

export const cameraService = {
  getAll: async (params?: any): Promise<PagedResponse<Camera>> => {
    const response = await api.get<ApiResponse<PagedResponse<Camera>>>('/cameras', { params });
    return response.data.data;
  },

  getById: async (id: string): Promise<Camera> => {
    const response = await api.get<ApiResponse<Camera>>(`/cameras/${id}`);
    return response.data.data;
  },

  create: async (data: any): Promise<Camera> => {
    const response = await api.post<ApiResponse<Camera>>('/cameras', data);
    return response.data.data;
  },

  update: async (id: string, data: any): Promise<Camera> => {
    const response = await api.put<ApiResponse<Camera>>(`/cameras/${id}`, data);
    return response.data.data;
  },

  delete: async (id: string): Promise<void> => {
    await api.delete(`/cameras/${id}`);
  },

  testConnection: async (id: string): Promise<any> => {
    const response = await api.post(`/cameras/${id}/test-connection`);
    return response.data;
  },

  getCalibration: async (id: string): Promise<CameraCalibration> => {
    const response = await api.get<ApiResponse<CameraCalibration>>(`/cameras/${id}/calibration`);
    return response.data.data;
  },

  saveCalibration: async (id: string, data: any): Promise<CameraCalibration> => {
    const response = await api.post<ApiResponse<CameraCalibration>>(`/cameras/${id}/calibration`, data);
    return response.data.data;
  },

  getHealth: async (id: string): Promise<PagedResponse<CameraHealthEvent>> => {
    const response = await api.get<ApiResponse<PagedResponse<CameraHealthEvent>>>(`/cameras/${id}/health`);
    return response.data.data;
  },

  startStream: async (id: string): Promise<void> => {
    await api.post(`/cameras/${id}/stream/start`);
  },

  stopStream: async (id: string): Promise<void> => {
    await api.post(`/cameras/${id}/stream/stop`);
  },

  getStreamStatus: async (id: string): Promise<StreamStatus> => {
    const response = await api.get<ApiResponse<StreamStatus>>(`/cameras/${id}/stream/status`);
    return response.data.data;
  },

  getStreamUrl: (id: string, ts?: number): string => {
    const base = `/api/v1/cameras/${id}/stream/mjpeg`;
    if (ts) return `${base}?_=${ts}`;
    return base;
  },

  updateSourceUrl: async (id: string, sourceUrl: string): Promise<Camera> => {
    const response = await api.patch<ApiResponse<Camera>>(`/cameras/${id}/source-url`, { sourceUrl });
    return response.data.data;
  },

  geocode: async (query: string, limit: number = 10): Promise<any[]> => {
    const response = await api.post('/cameras/location/geocode', { query, limit });
    return response.data.data;
  },

  reverseGeocode: async (latitude: number, longitude: number): Promise<any> => {
    const response = await api.post('/cameras/location/reverse-geocode', { latitude, longitude });
    return response.data.data;
  },
};
