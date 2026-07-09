import React, { useState, useEffect, useRef, useCallback } from 'react';
import { MapContainer, TileLayer, Marker, useMapEvents, useMap } from 'react-leaflet';
import L from 'leaflet';
import { cameraService } from '../services/cameraService';

export type LocationSource = 'DEVICE_GEOLOCATION' | 'MAP_SELECTION' | 'ADDRESS_SEARCH' | 'MANUAL_COORDINATES' | 'CAMERA_METADATA' | 'IMPORT' | 'UNKNOWN';

export interface LocationState {
  latitude: number | null;
  longitude: number | null;
  locationSource: LocationSource;
  locationAccuracyMeters: number | null;
  address: string;
}

interface CameraLocationSectionProps {
  location: LocationState;
  onChange: (loc: LocationState) => void;
  readOnly?: boolean;
}

const DEFAULT_CENTER: [number, number] = [27.7172, 85.3240];

const markerIcon = L.divIcon({
  className: 'custom-marker-icon',
  html: `<div style="width:24px;height:24px;background:#1e40af;border:3px solid #fff;border-radius:50%;box-shadow:0 2px 6px rgba(0,0,0,0.3);"></div>`,
  iconSize: [24, 24],
  iconAnchor: [12, 12],
  popupAnchor: [0, -12],
});

function DraggableMarker({ lat, lng, onMove }: { lat: number; lng: number; onMove: (lat: number, lng: number) => void }) {
  const markerRef = useRef<L.Marker>(null);

  const eventHandlers = {
    dragend() {
      const marker = markerRef.current;
      if (marker) {
        const pos = marker.getLatLng();
        onMove(pos.lat, pos.lng);
      }
    },
  };

  return (
    <Marker
      ref={markerRef}
      position={[lat, lng]}
      icon={markerIcon}
      draggable={true}
      eventHandlers={eventHandlers}
    />
  );
}

function MapClickHandler({ onClick }: { onClick: (lat: number, lng: number) => void }) {
  useMapEvents({
    click(e) {
      onClick(e.latlng.lat, e.latlng.lng);
    },
  });
  return null;
}

function MapCenterUpdater({ lat, lng }: { lat: number; lng: number }) {
  const map = useMap();
  useEffect(() => {
    if (lat !== 0 && lng !== 0) {
      map.setView([lat, lng], map.getZoom() < 14 ? 15 : map.getZoom());
    }
  }, [lat, lng, map]);
  return null;
}

const CameraLocationSection: React.FC<CameraLocationSectionProps> = ({ location, onChange, readOnly }) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<any[]>([]);
  const [showResults, setShowResults] = useState(false);
  const [geolocating, setGeolocating] = useState(false);
  const [geolocationError, setGeolocationError] = useState<string | null>(null);
  const [searching, setSearching] = useState(false);
  const [manualLat, setManualLat] = useState(location.latitude !== null ? String(location.latitude) : '');
  const [manualLng, setManualLng] = useState(location.longitude !== null ? String(location.longitude) : '');
  const [coordError, setCoordError] = useState<string | null>(null);
  const [reverseGeocoding, setReverseGeocoding] = useState(false);

  const debounceRef = useRef<ReturnType<typeof setTimeout>>();
  const abortRef = useRef<AbortController>();

  const updateLocation = useCallback((partial: Partial<LocationState>) => {
    onChange({ ...location, ...partial });
  }, [location, onChange]);

  const handleMapClick = useCallback((lat: number, lng: number) => {
    if (readOnly) return;
    setManualLat(lat.toFixed(6));
    setManualLng(lng.toFixed(6));
    updateLocation({
      latitude: lat,
      longitude: lng,
      locationSource: 'MAP_SELECTION',
      locationAccuracyMeters: null,
      address: '',
    });
    reverseGeocodeCoord(lat, lng);
  }, [readOnly, updateLocation]);

  const handleMarkerDrag = useCallback((lat: number, lng: number) => {
    setManualLat(lat.toFixed(6));
    setManualLng(lng.toFixed(6));
    updateLocation({
      latitude: lat,
      longitude: lng,
      locationSource: 'MAP_SELECTION',
      locationAccuracyMeters: null,
    });
    clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => reverseGeocodeCoord(lat, lng), 800);
  }, [updateLocation]);

  const handleCurrentLocation = useCallback(() => {
    if (readOnly) return;
    setGeolocationError(null);

    if (!navigator.geolocation) {
      setGeolocationError('Automatic location is unavailable. Select the camera location on the map.');
      return;
    }

    setGeolocating(true);

    navigator.geolocation.getCurrentPosition(
      (position) => {
        const { latitude, longitude, accuracy } = position.coords;
        setManualLat(latitude.toFixed(6));
        setManualLng(longitude.toFixed(6));

        updateLocation({
          latitude,
          longitude,
          locationSource: 'DEVICE_GEOLOCATION',
          locationAccuracyMeters: accuracy,
          address: '',
        });

        setGeolocating(false);

        reverseGeocodeCoord(latitude, longitude);

        setGeolocationError(null);
      },
      (error) => {
        setGeolocating(false);
        switch (error.code) {
          case error.PERMISSION_DENIED:
            setGeolocationError('Location permission was denied.');
            break;
          case error.POSITION_UNAVAILABLE:
            setGeolocationError('Unable to determine current device location.');
            break;
          case error.TIMEOUT:
            setGeolocationError('Location detection timed out.');
            break;
          default:
            setGeolocationError('Unable to determine current device location.');
            break;
        }
      },
      {
        enableHighAccuracy: true,
        timeout: 10000,
        maximumAge: 60000,
      }
    );
  }, [readOnly, updateLocation]);

  const handleSearch = useCallback((query: string) => {
    setSearchQuery(query);
    setShowResults(false);
    setCoordError(null);

    if (abortRef.current) {
      abortRef.current.abort();
    }

    if (query.trim().length < 3) {
      setSearchResults([]);
      return;
    }

    clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(async () => {
      setSearching(true);
      try {
        const results = await cameraService.geocode(query, 8);
        setSearchResults(results);
        setShowResults(results.length > 0);
      } catch (err: any) {
        if (err?.name !== 'CanceledError') {
          setSearchResults([]);
        }
      } finally {
        setSearching(false);
      }
    }, 500);
  }, []);

  const handleSelectSearchResult = useCallback((result: any) => {
    const lat = parseFloat(result.latitude);
    const lng = parseFloat(result.longitude);
    setManualLat(lat.toFixed(6));
    setManualLng(lng.toFixed(6));
    setSearchQuery(result.displayName || '');
    setShowResults(false);
    updateLocation({
      latitude: lat,
      longitude: lng,
      locationSource: 'ADDRESS_SEARCH',
      locationAccuracyMeters: null,
      address: result.displayName || '',
    });
  }, [updateLocation]);

  const handleManualLatChange = useCallback((value: string) => {
    setManualLat(value);
    setCoordError(null);

    const lat = parseFloat(value);
    if (isNaN(lat) || value === '') return;

    if (lat < -90 || lat > 90) {
      setCoordError('Latitude must be between -90 and 90.');
      return;
    }

    const lng = parseFloat(manualLng);
    if (!isNaN(lng) && lng >= -180 && lng <= 180) {
      updateLocation({
        latitude: lat,
        longitude: lng,
        locationSource: 'MANUAL_COORDINATES',
        locationAccuracyMeters: null,
      });
    }
  }, [manualLng, updateLocation]);

  const handleManualLngChange = useCallback((value: string) => {
    setManualLng(value);
    setCoordError(null);

    const lng = parseFloat(value);
    if (isNaN(lng) || value === '') return;

    if (lng < -180 || lng > 180) {
      setCoordError('Longitude must be between -180 and 180.');
      return;
    }

    const lat = parseFloat(manualLat);
    if (!isNaN(lat) && lat >= -90 && lat <= 90) {
      updateLocation({
        latitude: lat,
        longitude: lng,
        locationSource: 'MANUAL_COORDINATES',
        locationAccuracyMeters: null,
      });
    }
  }, [manualLat, updateLocation]);

  const reverseGeocodeCoord = useCallback(async (lat: number, lng: number) => {
    setReverseGeocoding(true);
    try {
      const result = await cameraService.reverseGeocode(lat, lng);
      if (result && result.displayName) {
        setSearchQuery(result.displayName);
        updateLocation({ address: result.displayName });
      }
    } catch {
      // reverse geocoding is best-effort
    } finally {
      setReverseGeocoding(false);
    }
  }, [updateLocation]);

  const sourceLabel = (source: LocationSource): string => {
    switch (source) {
      case 'DEVICE_GEOLOCATION': return 'Current Device Location';
      case 'MAP_SELECTION': return 'Map Selection';
      case 'ADDRESS_SEARCH': return 'Address Search';
      case 'MANUAL_COORDINATES': return 'Manual Entry';
      case 'CAMERA_METADATA': return 'Camera Metadata';
      case 'IMPORT': return 'Import';
      default: return 'Unknown';
    }
  };

  const hasValidCoords = location.latitude !== null && location.longitude !== null
    && location.latitude >= -90 && location.latitude <= 90
    && location.longitude >= -180 && location.longitude <= 180;

  return (
    <div className="camera-location-section">
      <h3>Camera Location</h3>
      <p className="text-muted" style={{ fontSize: 12, marginBottom: 12 }}>
        Configure the physical installation location of this camera.
        {location.locationSource === 'DEVICE_GEOLOCATION' && (
          <span className="location-note"> Current device location detected. Confirm that this is the camera installation location.</span>
        )}
      </p>

      <div className="location-methods">
        <div className="location-method-row">
          <button
            type="button"
            className="btn-secondary btn-sm"
            onClick={handleCurrentLocation}
            disabled={readOnly || geolocating}
          >
            {geolocating ? 'Detecting...' : 'Use My Current Location'}
          </button>
          {geolocationError && (
            <span className="location-error">{geolocationError}</span>
          )}
        </div>

        <div className="location-search-row">
          <div className="location-search-wrapper">
            <input
              type="text"
              className="location-search-input"
              placeholder="Search location or address..."
              value={searchQuery}
              onChange={e => handleSearch(e.target.value)}
              disabled={readOnly}
            />
            {searching && <span className="search-spinner">Searching...</span>}
            {showResults && (
              <div className="location-search-results">
                {searchResults.map((result, idx) => (
                  <div
                    key={idx}
                    className="location-search-item"
                    onClick={() => handleSelectSearchResult(result)}
                  >
                    <div className="search-item-name">{result.displayName}</div>
                    <div className="search-item-coords">
                      {parseFloat(result.latitude).toFixed(4)}, {parseFloat(result.longitude).toFixed(4)}
                      {result.type ? ` · ${result.type}` : ''}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>

        <div className="location-coord-row">
          <div className="form-group coord-input">
            <label>Latitude</label>
            <input
              type="number"
              step="any"
              min="-90"
              max="90"
              placeholder="27.7172"
              value={manualLat}
              onChange={e => handleManualLatChange(e.target.value)}
              disabled={readOnly}
            />
          </div>
          <div className="form-group coord-input">
            <label>Longitude</label>
            <input
              type="number"
              step="any"
              min="-180"
              max="180"
              placeholder="85.3240"
              value={manualLng}
              onChange={e => handleManualLngChange(e.target.value)}
              disabled={readOnly}
            />
          </div>
          {coordError && (
            <div className="coord-error">{coordError}</div>
          )}
        </div>

        <div className="location-map-wrapper">
          <MapContainer
            center={hasValidCoords ? [location.latitude!, location.longitude!] : DEFAULT_CENTER}
            zoom={hasValidCoords ? 15 : 12}
            className="leaflet-map"
            style={{ height: '280px', borderRadius: '8px' }}
          >
            <TileLayer
              attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
              url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            />
            {!readOnly && (
              <MapClickHandler onClick={handleMapClick} />
            )}
            {hasValidCoords && (
              <DraggableMarker
                lat={location.latitude!}
                lng={location.longitude!}
                onMove={handleMarkerDrag}
              />
            )}
            {hasValidCoords && (
              <MapCenterUpdater lat={location.latitude!} lng={location.longitude!} />
            )}
          </MapContainer>
          <div className="location-map-hint">
            {readOnly ? '' : 'Click on the map to place the marker. Drag to reposition.'}
          </div>
        </div>

        {location.locationSource && location.locationSource !== 'UNKNOWN' && (
          <div className="location-source-info">
            <strong>Location Source:</strong> {sourceLabel(location.locationSource)}
            {location.locationAccuracyMeters != null && (
              <span className="accuracy-info">
                · Accuracy: ±{location.locationAccuracyMeters.toFixed(0)}m
              </span>
            )}
          </div>
        )}

        {location.address && (
          <div className="location-address-info">
            <strong>Address:</strong> {location.address}
          </div>
        )}

        {!hasValidCoords && !readOnly && (
          <div className="location-not-configured" style={{ marginTop: 8, fontSize: 13, color: '#f59e0b' }}>
            Location Not Configured — this camera will not appear on the Traffic Intelligence Map.
          </div>
        )}
      </div>
    </div>
  );
};

export default CameraLocationSection;
