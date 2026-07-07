# RoadEye — Agent Development Guide

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
