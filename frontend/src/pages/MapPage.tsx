import React, { useEffect, useState } from 'react';
import { MapContainer, TileLayer, Marker, Popup } from 'react-leaflet';
import { useQuery } from '@tanstack/react-query';
import { cameraService } from '../services/cameraService';
import L from 'leaflet';

const MapPage: React.FC = () => {
  const [center] = useState<[number, number]>([27.7172, 85.3240]); // Kathmandu
  const { data: camerasData } = useQuery({
    queryKey: ['cameras-map'],
    queryFn: () => cameraService.getAll({ page: 0, size: 100 }),
  });

  const cameras = camerasData?.content?.filter(c => c.latitude && c.longitude) || [];

  const getMarkerColor = (status: string) => {
    switch (status) {
      case 'ONLINE': return '#22c55e';
      case 'OFFLINE': return '#ef4444';
      case 'DEGRADED': return '#f97316';
      default: return '#6b7280';
    }
  };

  return (
    <div className="map-page">
      <h2>Traffic Map</h2>
      <div className="map-container">
        <MapContainer center={center} zoom={12} className="leaflet-map">
          <TileLayer
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          />
          {cameras.map((camera) => (
            <Marker
              key={camera.id}
              position={[camera.latitude, camera.longitude]}
            >
              <Popup>
                <strong>{camera.name}</strong><br />
                Code: {camera.cameraCode}<br />
                Status: {camera.healthStatus}<br />
                Location: {camera.location || '-'}
              </Popup>
            </Marker>
          ))}
        </MapContainer>
      </div>
    </div>
  );
};

export default MapPage;
