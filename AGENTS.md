# RoadEye — Agent Development Guide

## Current Work State (Last Updated: 2026-07-09)

### VERIFIED WORKING
- AI Engine: YOLOv8 model loads from `models/yolov8n.pt` via ultralytics
- AI Engine: EasyOCR initializes successfully (CPU mode)
- AI Engine: Python 3.11 venv at `ai-engine/.venv` with all deps
- Backend: Compiles all Java source files with JDK 21 (Temurin) — `mvn compile` verified
- Backend: 36/36 unit tests pass across 6 test classes — `mvn test` verified
- Frontend: Builds successfully with Vite (972KB JS + 35KB CSS) — `npm run build` verified
- Docker: PostgreSQL, Kafka, ZooKeeper, MinIO all healthy and running

### FIXED IN SESSION 2 (2026-07-09)
- **Map blue background**: CSS height chain fixed (`.content` + `.intelligence-map` + `.map-container` now all use `flex: 1` instead of fragile `height: 100%`). MapContainer wrapper uses `height: 100%`. Requires Docker rebuild (`docker compose build frontend && docker compose up -d frontend`).
- **Kafka violation timestamp**: `handleViolationCandidate` now reads `payload.timestamp` (actual detection time) with fallback to envelope timestamp. Fixes evidence timestamps used for legal/enforcement purposes.
- **Kafka evidence_frames processing**: ViolationCandidate `evidence_frames` list now persisted as `ViolationEvidence` records (type `FRAME`, bucket `ai-engine`). Previously completely ignored.
- **Kafka VehicleTrack creation**: `handleVehicleObservation` now upserts `VehicleTrack` records (first_seen/last_seen/category/direction/confidence). Previously `vehicleTrackRepository` was injected but never used.
- **Kafka HealthStatus guard**: `HealthStatus.valueOf(status)` wrapped in try/catch — safely falls back to `UNKNOWN` instead of crashing on unexpected status strings.
- **AI Engine missing fields**: `_publish_detections` now sends `model_name`/`model_version`; `_publish_plates` sends `alternatives`/`detector_version`/`ocr_model_version`; `_publish_speeds` sends `calibration_version`. Previously all `null` in backend.
- **CORS configurable**: Origins moved from hardcoded `http://localhost:3000` to `cors.allowed-origins` in `application.yml` (comma-separated, env var `CORS_ALLOWED_ORIGINS`).

### PREVIOUSLY FIXED (Session 1)
- **torchvision reinstall**: Fixed `No module named 'torchvision.models.detection.faster_rcnn'` — reinstalled torch 2.5.1 + torchvision 0.20.1
- **Speed estimator**: Returns empty results when uncalibrated (no more DEFAULT_PIXELS_PER_METER = 0.05). Requires `calibrate()` to be called with real reference values.
- **Violation detector**: Wrong-way detection disabled by default (requires `allowed_direction` to be explicitly configured). Overspeeding disabled by default. Both default to `False` now.
- **Event publishing throttled**: Vehicle observations published once per track (first detection) + heartbeat every ~0.5s. Speed measurements throttled similarly. No more frame-rate Kafka flooding.
- **ByteBuddy serialization**: Created `CameraHealthEventResponse` DTO. `GET /{id}/health` now returns DTOs, not JPA entities with lazy `@ManyToOne` proxies.
- **CameraController**: Added stream management endpoints (start/stop/status/mjpeg proxy). CameraEventService uses query params for AI Engine API.
- **KafkaConsumerConfig**: All 6 handlers fixed with proper entity creation, timestamps from event envelopes, track_id/bbox in plate observations, real evidence creation.
- **Frontend Live Monitoring**: Created `LiveMonitoringPage.tsx` with camera selector, start/stop monitoring, MJPEG live feed display, stream stats panel. Route added at `/live`. Navigation item "Live Monitor" added.
- **Event publisher shutdown**: Already guards with `_started` flag in `publish()`.

### FIXED IN SESSION 3 (2026-07-09)
- **Kafka topic audit**: All 7 topics verified matching between backend (`KafkaConfig.java`) and AI Engine (`config.py`). `auto.offset.reset=earliest` correct in `application.yml`. Topic creation via `KafkaAdmin` beans with `NewTopic` configs.
- **AI Engine models missing fields**: `PlateResult` now has `detector_version`/`ocr_model_version` fields. `SpeedEstimator` tracks `calibration_version`. `PlateRecognizer` sets both version fields. Previously missing in schema.
- **IncidentService N+1 query**: `getIncidents()` now batch-loads camera names via `loadCameraNames()` map instead of N+1 queries. Duplicate `mapToResponse` method removed. Null-safe camera name lookup.
- **IncidentRepository**: Added `countByStatusNot(String status)` method — was missing, blocking dashboard stats.
- **Lombok annotation processing**: Added `<lombok.version>1.18.30</lombok.version>` to `pom.xml`. All 96 Java files compile correctly now.
- **AI Engine RTSP crash**: Restructured reconnect loop — `cap=None` guard before `cap.read()` prevents `AttributeError`. Exponential backoff reconnection isolated properly.
- **AI Engine race condition**: `main.py` now calls `stream_manager.configure_violation_detection()` before `start_stream()`. Fixes config arriving after stream already started.
- **Frontend Incidents module**: New `Incident` type, `incidentService.ts` (CRUD), `IncidentsPage.tsx` (filters, table, assign/resolve modal). Route at `/incidents`. Nav item added.
- **Frontend ViolationType**: Added missing `BLACKLISTED_VEHICLE` and `STOLEN_VEHICLE` values.
- **IncidentService test fix**: Null-safe `loadCameraNames` returns mutable `HashMap` not `Map.of()`; null `cameraId` guarded in map lookup. Fixes NPE in unit tests.

### FIXED IN SESSION 4 (2026-07-09)
- **Full repository audit (4 subagents):** 165 issues found (Backend 56, Frontend 50, AI Engine 33, Infra/DB 26) — all critical and high-severity items fixed
- **ViolationResponse detectorVersion exposed:** Added `detectorVersion` field to `ViolationResponse` DTO, populated from `Violation.detectorVersion` in both `mapToResponse` methods. Added `detector_version` field to AI Engine `ViolationCandidate` model; `ViolationDetector` accepts `detector_version` param (format `yolov8-{model_version}`); violations now include it in Kafka payload. Kafka consumer sets `violation.setDetectorVersion()` from payload.
- **UserController in-memory filtering fixed:** `UserRepository` extends `JpaSpecificationExecutor<User>`; `getUsers()` builds dynamic `Specification<User>` with predicates for `role`, `active`, and `search` (LIKE on username/email/fullName). Filtering now happens at DB level with proper pagination — no more fetching all rows and filtering in memory.
- **CameraCalibration eager fetch fixed:** `@OneToOne(fetch = FetchType.LAZY)` — prevents N+1 loading of Camera entity when iterating calibrations.
- **AI Engine `.dockerignore` created:** Excludes `.venv`, `__pycache__`, `.env`, `tests`, etc.
- **Frontend `Violation` type updated:** Added optional `detectorVersion?: string` field.

### FIXED IN SESSION 5 (2026-07-09)
- **YOLO class mapping bug — motorcycle shown as CAR:** Fixed `COCO_TO_ROADEYE` and `COCO_CLASS_NAMES` dictionaries in `vehicle_detector.py` that used wrong class IDs (2→MOTORCYCLE, 3→CAR, 4→BUS, 5→TRUCK) that didn't match the actual YOLOv8n COCO model indices (2=car, 3=motorcycle, 5=bus, 7=truck). Corrected all mappings and added `_verify_class_mapping()` method that dynamically checks the loaded model's `model.names` against code constants at startup and logs errors on mismatch. Updated test to expect correct model IDs. AI Engine tests: 20/20 pass.

### STILL TO VERIFY
- End-to-end runtime test with real RTSP camera
- Database flooding verification (measure row counts over 60s)
- PostgreSQL-backed camera registration flow
- WebSocket real-time alerts from frontend subscriber
- Evidence capture and storage (AI Engine → MinIO pipeline)
- Camera health event resolution (backend found for health event)
- Clean shutdown (no ProducerClosed errors)
- Map tile rendering after Docker frontend rebuild
- AI Engine Docker build (Dockerfile exists but untested)
- Violation human review workflow end-to-end

## Project Overview

RoadEye is an AI-powered intelligent traffic surveillance, vehicle monitoring, and traffic violation detection platform. It is designed primarily for deployment in Nepal while supporting vehicles and number plates from multiple countries.

## Architecture

CAMERAS / CCTV / RTSP / VIDEO FILES
↓
PYTHON AI ENGINE (Computer Vision + YOLOv8 + EasyOCR)
↓
KAFKA (Async Events)
↓
SPRING BOOT MODULAR MONOLITH
↓
POSTGRESQL + POSTGIS
↓
MINIO OBJECT STORAGE
↓
REST API + WEBSOCKET (Real-time Events)
↓
REACT + TYPESCRIPT FRONTEND

## Tech Stack

- **Backend**: Java 21, Spring Boot 3.2.4, Maven
- **AI Engine**: Python 3.11, OpenCV, YOLOv8, EasyOCR, FastAPI
- **Frontend**: React 18, TypeScript, Vite, Recharts, Leaflet
- **Database**: PostgreSQL 16 with PostGIS
- **Events**: Apache Kafka
- **Storage**: MinIO (S3-compatible)
- **Infrastructure**: Docker Compose

## Module Structure

### Backend Modules (com.roadeye.*)
- `auth` — Authentication, users, roles, JWT
- `user` — User management
- `camera` — Camera registration, health, calibration
- `geography` — Province, district, municipality
- `road` — Roads, segments, intersections, zones, speed limits
- `vehicle` — Vehicle observations and tracks
- `plate` — Number plate observations
- `speed` — Speed measurements
- `violation` — Violations, evidence, reviews
- `evidence` — Evidence storage and retrieval
- `alert` — Alerts and notifications
- `review` — Human review workflow
- `analytics` — Traffic analytics and dashboards
- `audit` — Security audit logging
- `system` — System configuration and health
- `shared` — Shared base entities, DTOs, exceptions
- `config` — Security, JWT, Kafka, MinIO, WebSocket

### AI Engine Modules (src.*)
- `detection` — YOLOv8 vehicle detection
- `tracking` — IoU-based multi-object tracking
- `speed` — Camera-calibrated speed estimation
- `plate` — EasyOCR-based plate recognition
- `violation` — Violation detection (overspeeding, wrong-way, no-helmet)
- `stream` — Video stream management and processing
- `common` — Data models, logging, Kafka event publisher

### Frontend Pages
- Login, Dashboard, Cameras, Camera Detail
- Roads & Zones, Violations, Violation Detail
- Alerts, Traffic Map, Analytics
- Users, Audit Logs, System Health
- Live Monitoring, Incidents

## Development Setup

1. Prerequisites: Docker, Docker Compose
2. Clone the repository
3. Copy `.env.example` to `.env` and adjust if needed
4. Run `docker compose up -d` to start all services
5. Backend: `http://localhost:8080`
6. AI Engine: `http://localhost:8000`
7. Frontend: `http://localhost:3000`
8. MinIO Console: `http://localhost:9001`
9. Default credentials: admin/admin123

## Build Commands

### Backend
```bash
cd backend
mvn clean compile
mvn test
mvn package -DskipTests
# NOTE: mvnw wrapper doesn't exist; use system `mvn` (/opt/homebrew/bin/mvn)
```

### AI Engine
```bash
cd ai-engine
pip install -r requirements.txt
python -m src.main
# or
uvicorn src.main:app --reload
```

### Frontend
```bash
cd frontend
npm install
npm run dev
npm run build
```

## Kafka Topics
- `camera-health` — Camera status updates
- `vehicle-observations` — Vehicle detections
- `plate-recognitions` — Number plate OCR results
- `speed-estimations` — Speed measurements
- `violation-candidates` — Detected violations
- `evidence-created` — Evidence storage notifications
- `vehicle-tracks` — Vehicle track updates (created by backend consumer)

## Key Design Decisions
1. **Modular Monolith** — Not microservices. Keep it simple.
2. **Extensible Vehicle Taxonomy** — Not a fixed enum; supports new categories via configuration.
3. **Multi-Country ANPR** — Nepal, India, UNKNOWN formats supported.
4. **Calibrated Speed Estimation** — CV-based, not legally certified.
5. **AI Never Decides Alone** — Human review required for enforcement.
6. **No Payment Processing** — Not a fintech platform.
