import api from './api';
import { Violation, ApiResponse, PagedResponse } from '../types';

export const violationService = {
  getAll: async (params?: any): Promise<PagedResponse<Violation>> => {
    const response = await api.get<ApiResponse<PagedResponse<Violation>>>('/violations', { params });
    return response.data.data;
  },

  getById: async (id: string): Promise<Violation> => {
    const response = await api.get<ApiResponse<Violation>>(`/violations/${id}`);
    return response.data.data;
  },

  updateStatus: async (id: string, status: string, comment?: string): Promise<Violation> => {
    const response = await api.patch<ApiResponse<Violation>>(`/violations/${id}/status`, { status, comment });
    return response.data.data;
  },

  getEvidence: async (id: string): Promise<any[]> => {
    const response = await api.get<ApiResponse<any[]>>(`/violations/${id}/evidence`);
    return response.data.data;
  },
};
