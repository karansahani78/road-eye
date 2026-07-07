import api from './api';
import { LoginRequest, LoginResponse, User, ApiResponse } from '../types';

export const authService = {
  login: async (data: LoginRequest): Promise<LoginResponse> => {
    const response = await api.post<ApiResponse<LoginResponse>>('/auth/login', data);
    return response.data.data;
  },

  register: async (data: any): Promise<User> => {
    const response = await api.post<ApiResponse<User>>('/auth/register', data);
    return response.data.data;
  },

  refresh: async (refreshToken: string): Promise<LoginResponse> => {
    const response = await api.post<ApiResponse<LoginResponse>>('/auth/refresh', { refreshToken });
    return response.data.data;
  },

  logout: async (): Promise<void> => {
    await api.post('/auth/logout');
  },

  getMe: async (): Promise<User> => {
    const response = await api.get<ApiResponse<User>>('/auth/me');
    return response.data.data;
  },
};
