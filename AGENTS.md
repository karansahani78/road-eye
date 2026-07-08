# RoadEye — Agent Development Guide

## Current Work State (Last Updated: 2026-07-08)

### VERIFIED WORKING
- AI Engine: YOLOv8 model loads from `models/yolov8n.pt` via ultralytics
- AI Engine: EasyOCR initializes successfully (CPU mode)
- AI Engine: Python 3.11 venv at `ai-engine/.venv` with all deps
- Backend: Compiles all 96 Java source files with JDK 21 (Temurin)
- Frontend: Builds successfully with Vite (938KB bundle)
- Docker: PostgreSQL, Kafka, ZooKeeper, MinIO all healthy and running

### FIXED IN CURRENT SESSION
- **torchvision reinstall**: Fixed `No module named 'torchvision.models.detection.faster_rcnn'` — reinstalled torch 2.5.1 + torchvision 0.20.1
- **Speed estimator**: Returns empty results when uncalibrated (no more DEFAULT_PIXELS_PER_METER = 0.05). Requires `calibrate()` to be called with real reference values.
- **Violation detector**: Wrong-way detection disabled by default (requires `allowed_direction` to be explicitly configured). Overspeeding disabled by default. Both default to `False` now.
- **Event publishing throttled**: Vehicle observations published once per track (first detection) + heartbeat every ~0.5s. Speed measurements throttled similarly. No more frame-rate Kafka flooding.
- **ByteBuddy serialization**: Created `CameraHealthEventResponse` DTO. `GET /{id}/health` now returns DTOs, not JPA entities with lazy `@ManyToOne` proxies.
- **CameraController**: Added stream management endpoints (start/stop/status/mjpeg proxy). CameraEventService uses query params for AI Engine API.
- **KafkaConsumerConfig**: All 6 handlers fixed with proper entity creation, timestamps from event envelopes, track_id/bbox in plate observations, real evidence creation.
- **Frontend Live Monitoring**: Created `LiveMonitoringPage.tsx` with camera selector, start/stop monitoring, MJPEG live feed display, stream stats panel. Route added at `/live`. Navigation item "Live Monitor" added.
- **Event publisher shutdown**: Already guards with `_started` flag in `publish()`.

### STILL TO VERIFY
- End-to-end runtime test with real RTSP camera
- Database flooding verification (measure row counts over 60s)
- PostgreSQL-backed camera registration flow
- WebSocket real-time alerts from frontend subscriber
- Evidence capture and storage
- Camera health event resolution (backend found for health event)
- Clean shutdown (no ProducerClosed errors)

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
./mvnw clean compile
./mvnw test
./mvnw package -DskipTests
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

## Key Design Decisions
1. **Modular Monolith** — Not microservices. Keep it simple.
2. **Extensible Vehicle Taxonomy** — Not a fixed enum; supports new categories via configuration.
3. **Multi-Country ANPR** — Nepal, India, UNKNOWN formats supported.
4. **Calibrated Speed Estimation** — CV-based, not legally certified.
5. **AI Never Decides Alone** — Human review required for enforcement.
6. **No Payment Processing** — Not a fintech platform.
