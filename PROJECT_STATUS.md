# RoadEye — Project Status

## Current Phase: Demo-Ready Audit Complete

### Last Updated: 2026-07-13

---

## Build Verification

| Component | Status | Tests | Details |
|-----------|--------|-------|---------|
| Backend (Java 21) | BUILD SUCCESS | 43/43 pass | `mvn compile && mvn test` verified |
| Frontend (React/TS) | BUILD SUCCESS | TypeScript clean | `npx tsc --noEmit && npm run build` verified |
| AI Engine (Python) | BUILD SUCCESS | 64/64 pass | `pytest tests/ -q` verified |
| Docker Compose | CONFIG VALID | All services configured | Health checks, networks, volumes verified |

---

## Session Work Summary (2026-07-13)

### Critical Fixes
1. **IncidentController @PreAuthorize** — Changed 4x `hasAnyAuthority` → `hasAnyRole` (was broken because JWT filter prepends `ROLE_` prefix)
2. **JwtAuthenticationFilter shouldNotFilter** — Changed from `startsWith` to `contains` (exact match only)
3. **WebSocket CORS origins** — Changed from `*` to configurable `cors.allowed-origins`
4. **Hardcoded secrets** — Fixed JWT secret, encryption key, and DB defaults in `application.yml`

### Infrastructure
5. **Docker** — Added `restart: unless-stopped`, pinned MinIO version, resource limits for all services
6. **Frontend Dockerfile** — Changed `npm install` → `npm ci`
7. **Nginx** — Added security headers, gzip compression, static asset caching
8. **`.env.example`** — Created with all required environment variables

### Frontend
9. **MonitoringContext** — App-level context persists selected cameras/layout across page navigation
10. **Dark Theme** — Complete CSS overhaul with professional surveillance-style dark UI
11. **Dashboard** — "Command Center" with system status, quick actions
12. **Camera Onboarding** — Auto-detect source type, description field, better empty states

### Backend
13. **Analytics N+1 Fixed** — Traffic volume: 168 queries → 1 batch. Violation trends: 60 queries → 2 batch. Vehicle categories: N queries → 1 batch
14. **TrafficMap N+1 Fixed** — 4*M+6 queries → 6 batch queries (M = mapped cameras)
15. **Data Retention Service** — Scheduled daily cleanup: vehicle obs (90d), tracks (30d), speeds (90d), health events (30d), audit logs (180d), plates (90d)
16. **Repository Batch Methods** — Added grouped count queries to VehicleObservation, Violation, Alert, SpeedMeasurement, VehicleTrack, CameraHealthEvent, AuditLog, PlateObservation repositories

---

## Architecture

```
CAMERAS / CCTV / RTSP / VIDEO FILES
    ↓
PYTHON AI ENGINE (YOLOv8 + EasyOCR)
    ↓
KAFKA (7 Topics)
    ↓
SPRING BOOT MODULAR MONOLITH (12 Modules)
    ↓
POSTGRESQL + POSTGIS
    ↓
MINIO OBJECT STORAGE
    ↓
REST API + WEBSOCKET
    ↓
REACT + TYPESCRIPT FRONTEND (Dark Theme)
```

---

## Key Files Modified (This Session)

| File | Change |
|------|--------|
| `backend/.../IncidentController.java` | `hasAnyAuthority` → `hasAnyRole` |
| `backend/.../JwtAuthenticationFilter.java` | `shouldNotFilter` exact match |
| `backend/.../WebSocketConfig.java` | CORS origins configurable |
| `backend/.../application.yml` | Secrets, DB defaults |
| `docker-compose.yml` | Restart policies, MinIO pin, resource limits |
| `frontend/nginx.conf` | Security headers, gzip, caching |
| `frontend/src/styles/global.css` | Dark theme overhaul |
| `frontend/src/pages/DashboardPage.tsx` | Command Center redesign |
| `frontend/src/components/Layout.tsx` | Role display fix |
| `frontend/src/hooks/useMonitoring.tsx` | Created app-level context |
| `backend/.../AnalyticsController.java` | N+1 batch queries |
| `backend/.../TrafficMapService.java` | N+1 batch queries |
| `backend/.../DataRetentionService.java` | Created scheduled cleanup |

---

## Remaining Items

### Still To Verify
- End-to-end runtime test with real RTSP camera
- WebSocket real-time alerts from frontend subscriber
- Evidence capture and storage (AI Engine → MinIO pipeline)
- Clean shutdown (no ProducerClosed errors)
- Map tile rendering after Docker frontend rebuild

### Technical Limitations (Unchanged)
- No dedicated license plate detection model (uses naive bbox crop + EasyOCR)
- YOLOv8n is nano variant — upgrade to v8m/v8l for better accuracy
- No Nepal-specific fine-tuning
- Speed requires explicit calibration
- No GPU utilization (CPU-only inference)
- No consumer retry/DLQ for Kafka

### Potential Improvements
- Frontend code-splitting (currently 984KB JS bundle)
- WebSocket consumer group for real-time dashboard updates
- Audit log retention configurable via admin UI
- Camera connection test endpoint (currently frontend-only validation)

---

## Test Credentials
- **Backend**: `http://localhost:8080`
- **Frontend**: `http://localhost:3000`
- **AI Engine**: `http://localhost:8000`
- **MinIO Console**: `http://localhost:9001`
- **DB**: user=`roadeye`, pass=`roadeye_dev`
- **Default login**: admin / admin123
