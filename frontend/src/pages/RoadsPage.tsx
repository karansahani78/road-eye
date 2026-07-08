import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import api from '../services/api';
import { Road, ApiResponse, PagedResponse } from '../types';

type Tab = 'roads' | 'segments' | 'zones' | 'intersections' | 'speed-limits';

const tabEndpoints: Record<Tab, string> = {
  roads: '/roads',
  segments: '/roads/segments',
  zones: '/roads/zones',
  intersections: '/roads/intersections',
  'speed-limits': '/roads/speed-limits',
};

const RoadsPage: React.FC = () => {
  const [page, setPage] = useState(0);
  const [tab, setTab] = useState<Tab>('roads');

  const { data, isLoading, error } = useQuery({
    queryKey: ['roads-data', tab, page],
    queryFn: async () => {
      const response = await api.get<ApiResponse<PagedResponse<any>>>(tabEndpoints[tab], {
        params: { page, size: 10, sort: tab === 'roads' ? 'name,asc' : 'createdAt,desc' },
      });
      return response.data.data;
    },
  });

  const changeTab = (next: Tab) => {
    setTab(next);
    setPage(0);
  };

  const renderRows = () => {
    if (!data?.content?.length) {
      return (
        <tr>
          <td colSpan={8} className="empty-table">No records found</td>
        </tr>
      );
    }

    if (tab === 'roads') {
      return data.content.map((road: Road) => (
        <tr key={road.id}>
          <td>{road.code}</td>
          <td>{road.name}</td>
          <td>{road.roadType}</td>
          <td>{road.province}</td>
          <td>{road.district}</td>
          <td>{road.length}</td>
          <td>{road.speedLimit} km/h</td>
        </tr>
      ));
    }

    if (tab === 'segments') {
      return data.content.map((item: any) => (
        <tr key={item.id}>
          <td>{item.roadName || '-'}</td>
          <td>{item.name || '-'}</td>
          <td>{item.startPoint || '-'}</td>
          <td>{item.endPoint || '-'}</td>
          <td>{item.lengthMeters ?? '-'}</td>
          <td>{item.speedLimit ? `${item.speedLimit} km/h` : '-'}</td>
        </tr>
      ));
    }

    if (tab === 'zones') {
      return data.content.map((item: any) => (
        <tr key={item.id}>
          <td>{item.code || '-'}</td>
          <td>{item.name}</td>
          <td>{item.zoneType || '-'}</td>
          <td>{item.speedLimit ? `${item.speedLimit} km/h` : '-'}</td>
          <td>{item.restricted ? 'Yes' : 'No'}</td>
        </tr>
      ));
    }

    if (tab === 'intersections') {
      return data.content.map((item: any) => (
        <tr key={item.id}>
          <td>{item.code || '-'}</td>
          <td>{item.name}</td>
          <td>{item.latitude ?? '-'}, {item.longitude ?? '-'}</td>
          <td>{item.hasTrafficSignal ? 'Yes' : 'No'}</td>
        </tr>
      ));
    }

    return data.content.map((item: any) => (
      <tr key={item.id}>
        <td>{item.roadSegmentId || '-'}</td>
        <td>{item.cameraId || '-'}</td>
        <td>{item.vehicleCategory || '-'}</td>
        <td>{item.speedLimitKmh} km/h</td>
        <td>{item.validFrom || '-'} – {item.validTo || '-'}</td>
        <td>{item.active ? 'Active' : 'Inactive'}</td>
      </tr>
    ));
  };

  const renderHeader = () => {
    switch (tab) {
      case 'roads':
        return (
          <tr>
            <th>Code</th><th>Name</th><th>Type</th><th>Province</th><th>District</th><th>Length (km)</th><th>Speed Limit</th>
          </tr>
        );
      case 'segments':
        return (
          <tr>
            <th>Road</th><th>Segment</th><th>Start</th><th>End</th><th>Length (m)</th><th>Speed Limit</th>
          </tr>
        );
      case 'zones':
        return (
          <tr>
            <th>Code</th><th>Name</th><th>Type</th><th>Speed Limit</th><th>Restricted</th>
          </tr>
        );
      case 'intersections':
        return (
          <tr>
            <th>Code</th><th>Name</th><th>Coordinates</th><th>Traffic Signal</th>
          </tr>
        );
      default:
        return (
          <tr>
            <th>Segment</th><th>Camera</th><th>Vehicle</th><th>Limit</th><th>Valid Hours</th><th>Status</th>
          </tr>
        );
    }
  };

  return (
    <div className="roads-page">
      <div className="page-header">
        <h2>Roads & Zones</h2>
      </div>

      <div className="filter-bar" style={{ marginBottom: '1rem', display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
        {(['roads', 'segments', 'zones', 'intersections', 'speed-limits'] as Tab[]).map((key) => (
          <button
            key={key}
            type="button"
            className={tab === key ? 'btn-primary btn-sm' : 'btn-secondary btn-sm'}
            onClick={() => changeTab(key)}
          >
            {key.replace('-', ' ').replace(/\b\w/g, (c) => c.toUpperCase())}
          </button>
        ))}
      </div>

      {error ? (
        <div className="error">Failed to load data</div>
      ) : isLoading ? (
        <div className="loading">Loading...</div>
      ) : (
        <>
          <div className="table-container">
            <table className="data-table">
              <thead>{renderHeader()}</thead>
              <tbody>{renderRows()}</tbody>
            </table>
          </div>
          {data && (
            <div className="pagination">
              <button disabled={data.first} onClick={() => setPage(page - 1)}>Previous</button>
              <span>Page {data.page + 1} of {Math.max(data.totalPages, 1)}</span>
              <button disabled={data.last} onClick={() => setPage(page + 1)}>Next</button>
            </div>
          )}
        </>
      )}
    </div>
  );
};

export default RoadsPage;
