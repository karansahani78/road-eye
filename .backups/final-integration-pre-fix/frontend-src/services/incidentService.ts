import api from './api';
import { Incident, ApiResponse, PagedResponse } from '../types';

export const incidentService = {
  getAll: async (params?: any): Promise<PagedResponse<Incident>> => {
    const response = await api.get<ApiResponse<PagedResponse<Incident>>>('/incidents', { params });
    return response.data.data;
  },

  getById: async (id: string): Promise<Incident> => {
    const response = await api.get<ApiResponse<Incident>>(`/incidents/${id}`);
    return response.data.data;
  },

  create: async (data: any): Promise<Incident> => {
    const response = await api.post<ApiResponse<Incident>>('/incidents', data);
    return response.data.data;
  },

  update: async (id: string, data: any): Promise<Incident> => {
    const response = await api.patch<ApiResponse<Incident>>(`/incidents/${id}`, data);
    return response.data.data;
  },

  assign: async (id: string, assignee?: string): Promise<Incident> => {
    const response = await api.post<ApiResponse<Incident>>(`/incidents/${id}/assign`, { assignee });
    return response.data.data;
  },

  resolve: async (id: string, notes?: string): Promise<Incident> => {
    const response = await api.post<ApiResponse<Incident>>(`/incidents/${id}/resolve`, notes, {
      headers: { 'Content-Type': 'text/plain' },
    });
    return response.data.data;
  },

  getStats: async (): Promise<any> => {
    const response = await api.get<ApiResponse<any>>('/incidents/stats');
    return response.data.data;
  },
};
