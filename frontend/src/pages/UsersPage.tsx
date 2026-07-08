import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import api from '../services/api';
import { User, ApiResponse, PagedResponse } from '../types';

const UsersPage: React.FC = () => {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);

  const { data, isLoading, error } = useQuery({
    queryKey: ['users', page],
    queryFn: async () => {
      const response = await api.get<ApiResponse<PagedResponse<User>>>('/users', {
        params: { page, size: 10, sort: 'createdAt,desc' },
      });
      return response.data.data;
    },
    retry: false,
  });

  const toggleStatusMutation = useMutation({
    mutationFn: async ({ id, active }: { id: string; active: boolean }) => {
      await api.patch(`/users/${id}/status`, { active: !active });
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['users'] }),
  });

  return (
    <div className="users-page">
      <div className="page-header">
        <h2>Users & Access Management</h2>
      </div>

      {error ? (
        <div className="error">You do not have permission to view users.</div>
      ) : isLoading ? (
        <div className="loading">Loading users...</div>
      ) : (
        <>
          <div className="table-container">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Username</th>
                  <th>Email</th>
                  <th>Full Name</th>
                  <th>Role</th>
                  <th>Status</th>
                  <th>Last Login</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {data?.content?.map((user: User) => (
                  <tr key={user.id}>
                    <td>{user.username}</td>
                    <td>{user.email}</td>
                    <td>{user.fullName || '-'}</td>
                    <td><span className="role-badge">{user.role}</span></td>
                    <td><span className={`status-badge ${user.active ? 'status-online' : 'status-offline'}`}>
                      {user.active ? 'Active' : 'Inactive'}</span></td>
                    <td>{user.lastLoginAt ? new Date(user.lastLoginAt).toLocaleString() : '-'}</td>
                    <td>
                      <button className="btn-sm btn-secondary"
                        onClick={() => toggleStatusMutation.mutate({ id: user.id, active: user.active })}>
                        {user.active ? 'Deactivate' : 'Activate'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {data && (
            <div className="pagination">
              <button disabled={data.first} onClick={() => setPage(page - 1)}>Previous</button>
              <span>Page {data.page + 1} of {data.totalPages}</span>
              <button disabled={data.last} onClick={() => setPage(page + 1)}>Next</button>
            </div>
          )}
        </>
      )}
    </div>
  );
};

export default UsersPage;
