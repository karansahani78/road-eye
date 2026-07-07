import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import api from '../services/api';
import { Road, ApiResponse, PagedResponse } from '../types';

const RoadsPage: React.FC = () => {
  const [page, setPage] = useState(0);

  const { data, isLoading } = useQuery({
    queryKey: ['roads', page],
    queryFn: async () => {
      const response = await api.get<ApiResponse<PagedResponse<Road>>>('/roads', {
        params: { page, size: 10, sort: 'name,asc' },
      });
      return response.data.data;
    },
  });

  return (
    <div className="roads-page">
      <div className="page-header">
        <h2>Roads & Zones</h2>
      </div>

      {isLoading ? (
        <div className="loading">Loading roads...</div>
      ) : (
        <>
          <div className="table-container">
            <table className="data-table">
              <thead>
                <tr>
                  <th>Code</th>
                  <th>Name</th>
                  <th>Type</th>
                  <th>Province</th>
                  <th>District</th>
                  <th>Length (km)</th>
                  <th>Speed Limit</th>
                </tr>
              </thead>
              <tbody>
                {data?.content?.map((road: Road) => (
                  <tr key={road.id}>
                    <td>{road.code}</td>
                    <td>{road.name}</td>
                    <td>{road.roadType}</td>
                    <td>{road.province}</td>
                    <td>{road.district}</td>
                    <td>{road.length}</td>
                    <td>{road.speedLimit} km/h</td>
                  </tr>
                ))}
                {(!data?.content || data.content.length === 0) && (
                  <tr><td colSpan={7} className="empty-table">No roads configured</td></tr>
                )}
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

export default RoadsPage;
