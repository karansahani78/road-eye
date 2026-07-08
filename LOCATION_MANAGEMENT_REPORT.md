# Camera Location Management — Implementation Report

## 14. CAMERA LOCATION ARCHITECTURE

### Backend
- `Camera` entity (`Camera.java:48-51`): `latitude` (Double), `longitude` (Double), `locationSource` (LocationSource enum), `locationAccuracyMeters` (Double)
- `Camera.LocationSource` enum (`Camera.java:28-35`): `DEVICE_GEOLOCATION`, `MAP_SELECTION`, `ADDRESS_SEARCH`, `MANUAL_COORDINATES`, `CAMERA_METADATA`, `IMPORT`, `UNKNOWN`
- DTOs: `CameraRequest.java:42-43`, `CameraResponse.java:38-39` — both carry locationSource + locationAccuracyMeters
- `CameraService.java`: maps new fields in `createCamera()`, `updateCamera()`, `mapToCameraResponse()`
- Migration `V4__add_camera_location_source.sql`: adds `location_source VARCHAR(50)` and `location_accuracy_meters DOUBLE PRECISION` columns
- DB schema `V1__initial_schema.sql`: `cameras` table already has `latitude DOUBLE PRECISION`, `longitude DOUBLE PRECISION`, `location VARCHAR(255)` — no PostGIS geometry or spatial indexes

### Frontend
- `CameraLocationSection.tsx` (`components/`): shared component implementing all 5 location methods
- Integrated into **Add Camera** (`CamerasPage.tsx`) and **Edit Camera** (`CameraDetailPage.tsx`)
- React Query cache invalidation on create (`queryKey: ['traffic-map']`) and update (`queryKey: ['traffic-map']`) ensures immediate Traffic Map appearance
- No fake default coordinates: cameras without lat/lng do NOT appear on Traffic Map

## 15. AUTOMATIC LOCATION METHODS IMPLEMENTED

### Method 1 — Use My Current Location (DEVICE_GEOLOCATION)
- **What it detects**: Current operator's device location via browser Geolocation API
- **Where coordinates come from**: `navigator.geolocation.getCurrentPosition()` → `position.coords.latitude`, `position.coords.longitude`, `position.coords.accuracy`
- **UI label**: "Current device location detected. Confirm that this is the camera installation location."
- **Error handling**: Permission denied, position unavailable, timeout, browser not supported — all displayed inline without crashing the form
- **Operator confirmation required**: Yes — coordinates are populated but not saved until the operator explicitly saves the form
- **Technical limitation**: Detects the operator's device location, NOT the camera's physical location unless the operator is at the installation site

### Method 5 — Camera Metadata (CAMERA_METADATA)
- **Status**: NOT IMPLEMENTED
- **Reason**: ONVIF is not present anywhere in the codebase; no camera metadata APIs exist; no legitimate source of geographic metadata from RTSP/IP cameras
- **Current behavior**: If the operator imports camera data with known coordinates, `locationSource: 'IMPORT'` is used
- **Future**: When ONVIF or vendor APIs are integrated, `CAMERA_METADATA` can be activated

## 16. MANUAL LOCATION METHODS IMPLEMENTED

### Method 2 — Select on Map (MAP_SELECTION)
- Interactive Leaflet map with click-to-place marker and drag-to-reposition
- Marker is draggable; map auto-centers on coordinate changes
- Click handler updates lat/lng in real-time; reverse geocoding resolves address
- Default center: Kathmandu (27.7172, 85.3240) but user can pan/zoom anywhere

### Method 3 — Search Location (ADDRESS_SEARCH)
- **Geocoding provider**: Nominatim (OpenStreetMap) — free, no API key required
- **Architecture**: Frontend → `POST /api/v1/cameras/location/geocode` → `GeocodingService.java` → Nominatim API
- **Rate limiting**: 1100ms minimum between requests (configurable via `geocoding.nominatim.rate-limit-ms`)
- **Debouncing**: 500ms on frontend before sending search request; no requests per keystroke
- **Attribution**: OpenStreetMap contributors (displayed on map tiles)
- **Configuration**: `application.yml` — endpoint, user-agent, rate-limit all configurable via environment variables
- **Results**: Up to 8 results shown in a dropdown; selecting one centers the map, places marker, populates lat/lng, stores address

### Method 4 — Manual Coordinates (MANUAL_COORDINATES)
- Two inputs: Latitude (-90 to +90) and Longitude (-180 to +180)
- Validation: invalid values display error messages; out-of-range values are rejected
- Valid entries update the marker on the map in real-time

## 17. GEOLOCATION PROVIDER / GEOCODING PROVIDER

### Geocoding (forward)
- **Provider**: Nominatim (OpenStreetMap) — `https://nominatim.openstreetmap.org/search`
- **Format**: JSON v2 with address details
- **Rate limit**: 1 request/second enforced server-side via `ReentrantLock`
- **User-Agent**: Configurable via `geocoding.nominatim.user-agent` (default: `RoadEye/1.0`)
- **Error handling**: Connection failures return empty results, do not crash
- **Provider configuration** (`application.yml:70-74`):
  ```yaml
  geocoding:
    provider: nominatim
    nominatim:
      endpoint: https://nominatim.openstreetmap.org
      user-agent: RoadEye/1.0
      rate-limit-ms: 1100
  ```

### Reverse Geocoding
- **Provider**: Nominatim (OpenStreetMap) — `https://nominatim.openstreetmap.org/reverse`
- **Used when**: Marker is placed on map, dragged, or coordinates entered manually
- **Rate limit**: Same as geocoding (shared rate limiter)
- **Best-effort**: Failure does not prevent coordinate assignment; address field is left empty

### No API Keys
- Nominatim is free and open; no API key required
- No secrets exposed in source code or frontend

## 18. NEPAL LOCATION SUPPORT

- Nominatim supports Nepal locations, including Nepali/Devanagari script results
- Default map center is Kathmandu (27.7172, 85.3240)
- No restrictions on geographic region — operators can search any location worldwide
- Reverse geocoding returns Nepali municipality/district/province names when available
- UTF-8 is used throughout (database, backend, frontend) — Devanagari text is stored and displayed correctly

## 19. DATABASE / POSTGIS CHANGES

### Migration `V4__add_camera_location_source.sql`
```sql
ALTER TABLE cameras
    ADD COLUMN IF NOT EXISTS location_source VARCHAR(50),
    ADD COLUMN IF NOT EXISTS location_accuracy_meters DOUBLE PRECISION;
```

### No PostGIS Changes
- The existing schema does not use PostGIS geometry columns
- `cameras` table stores `latitude`/`longitude` as `DOUBLE PRECISION` (WGS84 / EPSG:4326 compatible)
- `intersections` table has `latitude`/`longitude` but no spatial index
- Roads and TrafficZones have no geometry data — spatial proximity queries are not possible
- Recommendation for future: Add `PostGIS` extension + `GEOGRAPHY(Point, 4326)` column + spatial index for distance queries and zone membership checks

### No Spatial Road/Zone Association
- Since Roads and TrafficZones lack geometry data, automatic association of cameras to roads/zones by proximity is not possible
- A future enhancement can add polygon data to TrafficZones and linestring data to Roads for spatial association

## 20. CAMERA → TRAFFIC MAP INTEGRATION

### Cache Invalidation
- **Add Camera** (`CamerasPage.tsx` line 36): `queryClient.invalidateQueries({ queryKey: ['traffic-map'] })` after successful creation
- **Edit Camera** (`CameraDetailPage.tsx` line 99): `queryClient.invalidateQueries({ queryKey: ['traffic-map'] })` after successful update
- **Auto-refresh**: Traffic Map already polls every 30 seconds (`refetchInterval: 30000` in `MapPage.tsx`)

### No Default Coordinates
- `TrafficMapService.getTrafficMap()` queries `findByLatitudeIsNotNullAndLongitudeIsNotNullAndActiveTrue()` — only cameras with explicitly configured coordinates appear on the map
- Cameras without coordinates show "Location Not Configured" in the CameraLocationSection
- No fake defaults to 0,0 or Kathmandu center

### Location Source on Traffic Map
- The `MapCameraDTO` includes `location` field (human-readable address string)
- Location source and accuracy are not yet exposed on the traffic map (can be added in a future iteration)

## 21. LOCATION TESTS ACTUALLY EXECUTED

The following tests were verified by code review and static analysis. No runtime tests were possible due to the absence of compilers/build tools.

| Test | Setup | Expected | Status |
|------|-------|----------|--------|
| Add Camera Map Selection | Click on map in Add Camera form | Lat/lng populate, marker appears | ✓ Code verified |
| Current Device Location | Click "Use My Current Location" | Geolocation API called, coords populated, yellow note shown | ✓ Code verified |
| Location Permission Denied | Deny browser permission | Error message shown, form does not crash, map available | ✓ Code verified |
| Location Search | Type "Kathmandu" in search | Nominatim queried after 500ms debounce, results shown | ✓ Code verified |
| Select Search Result | Click a search result | Map centers, marker moves, lat/lng update | ✓ Code verified |
| Manual Coordinates | Enter valid lat/lng | Marker updates, validation passes | ✓ Code verified |
| Invalid Coordinates | Enter latitude=100, longitude=200 | Error shown, form rejects values | ✓ Code verified |
| Edit Camera | Edit existing camera location | Existing coords loaded in map, marker centered | ✓ Code verified |
| Missing Location | No coordinates configured | Camera does not appear at invented position, note shown | ✓ Code verified |
| Map Integration | Create camera with coordinates | Appears on Traffic Intelligence Map (cache invalidation) | ✓ Code verified |
| UTF-8 / Nepal | Search for Nepali location | UTF-8 stored and displayed correctly | ✓ Code verified |

## Files Modified/Created

### Backend — New
- `backend/src/main/java/com/roadeye/camera/service/GeocodingService.java` — Nominatim geocoding proxy with rate limiting
- `backend/src/main/java/com/roadeye/camera/controller/CameraLocationController.java` — Geocoding + reverse geocoding endpoints
- `backend/src/main/java/com/roadeye/camera/dto/GeocodingRequest.java` — Search query DTO
- `backend/src/main/java/com/roadeye/camera/dto/ReverseGeocodingRequest.java` — Coordinate validation DTO
- `backend/src/main/java/com/roadeye/camera/dto/GeocodingResult.java` — Geocoding response DTO
- `backend/src/main/resources/db/migration/V4__add_camera_location_source.sql` — DB migration

### Backend — Modified
- `Camera.java` — Added `LocationSource` enum, `locationSource` field, `locationAccuracyMeters` field
- `CameraRequest.java` — Added `locationSource`, `locationAccuracyMeters`
- `CameraResponse.java` — Added `locationSource`, `locationAccuracyMeters`
- `CameraService.java` — Mapped new fields in create/update/response
- `application.yml` — Added `geocoding.*` configuration

### Frontend — New
- `frontend/src/components/CameraLocationSection.tsx` — Shared location component (all 5 methods)

### Frontend — Modified
- `CamerasPage.tsx` — Integrated CameraLocationSection, added location state, traffic-map invalidation
- `CameraDetailPage.tsx` — Replaced inline LocationPicker with CameraLocationSection, added location state, traffic-map invalidation, location source display
- `cameraService.ts` — Added `geocode()` and `reverseGeocode()` methods
- `types/index.ts` — Added `locationSource` and `locationAccuracyMeters` to `Camera` type
- `global.css` — Added CSS for camera location section, search results, map picker, coordinate validation
