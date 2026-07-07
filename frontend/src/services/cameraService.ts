import api from './api';
import { Camera, CameraCalibration, ApiResponse, PagedResponse } from '../types';

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

  getHealth: async (id: string): Promise<any> => {
    const response = await api.get(`/cameras/${id}/health`);
    return response.data;
  },
};
