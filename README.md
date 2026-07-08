# RoadEye — AI Traffic Surveillance Platform

RoadEye is an intelligent traffic surveillance, vehicle monitoring, and traffic violation detection platform. Designed for Nepal while supporting vehicles and number plates from multiple countries.

## Architecture

```
CAMERAS / CCTV / RTSP / VIDEO FILES
↓
PYTHON AI ENGINE (Computer Vision + YOLOv8 + EasyOCR)
↓
KAFKA (Async Events)
↓
SPRING BOOT MODULAR MONOLITH
↓
POSTGRESQL + POSTGIS + MINIO
↓
REST API + WEBSOCKET
↓
REACT + TYPESCRIPT FRONTEND
```

## Quick Start

```bash
# 1. Clone and configure
git clone <repo-url> roadeye
cd roadeye
cp .env.example .env

# 2. Start all services
docker compose up -d

# 3. Access the platform
Frontend: http://localhost:3000
Backend API: http://localhost:8080
AI Engine: http://localhost:8000
MinIO Console: http://localhost:9001

# Default login: admin / admin123
```

## Features

- **Multi-Camera Surveillance** — RTSP, CCTV, IP cameras, video files
- **Vehicle Detection** — YOLOv8 with 35+ vehicle categories
- **Vehicle Tracking** — IoU-based multi-object tracking
- **Speed Estimation** — Calibrated computer vision
- **Multi-Country ANPR** — Nepal, India, UNKNOWN plate formats
- **Violation Detection** — Overspeeding, wrong-way, no-helmet
- **Evidence Management** — MinIO-based media storage
- **Real-time Alerts** — WebSocket notifications
- **Human Review** — Violation status workflow
- **Traffic Analytics** — Volume, trends, dashboards
- **Traffic Map** — Interactive Leaflet/OpenStreetMap
- **Security** — JWT auth, RBAC, audit logging

## Development

### Prerequisites
- Docker & Docker Compose
- Java 21 (for backend development)
- Python 3.11 (for AI engine)
- Node.js 20 (for frontend)

### Local Development

```bash
# Backend
cd backend
./mvnw spring-boot:run

# AI Engine
cd ai-engine
pip install -r requirements.txt
uvicorn src.main:app --reload

# Frontend
cd frontend
npm install
npm run dev

# Required infrastructure
docker compose up -d postgres kafka minio
```

## Project Structure

```
roadeye/
├── backend/          # Spring Boot modular monolith
│   ├── src/main/java/com/roadeye/
│   │   ├── auth/       # Authentication & users
│   │   ├── camera/     # Camera management
│   │   ├── geography/  # Provinces, districts
│   │   ├── road/       # Roads, segments, zones
│   │   ├── vehicle/    # Vehicle observations
│   │   ├── plate/      # Plate observations
│   │   ├── speed/      # Speed measurements
│   │   ├── violation/  # Violations & reviews
│   │   ├── alert/      # Alerts & notifications
│   │   ├── audit/      # Audit logging
│   │   ├── analytics/  # Traffic analytics
│   │   ├── system/     # System health
│   │   ├── shared/     # Base entities, DTOs
│   │   └── config/     # Security, Kafka, MinIO
│   └── src/main/resources/db/migration/
├── ai-engine/         # Python computer vision
│   └── src/
│       ├── detection/  # YOLOv8 vehicle detection
│       ├── tracking/   # Multi-object tracking
│       ├── speed/      # Speed estimation
│       ├── plate/      # ANPR with EasyOCR
│       ├── violation/  # Violation detection
│       └── stream/     # Video stream management
├── frontend/          # React + TypeScript
│   └── src/
│       ├── pages/      # Feature pages
│       ├── components/ # Shared components
│       ├── services/   # API clients
│       ├── hooks/      # React hooks
│       └── styles/     # CSS
├── docker-compose.yml
└── .env.example
```

## API Endpoints

| Group | Prefix | Description |
|-------|--------|-------------|
| Auth | `/api/v1/auth` | Login, register, refresh, logout |
| Users | `/api/v1/users` | User management |
| Cameras | `/api/v1/cameras` | Camera CRUD, calibration, health |
| Roads | `/api/v1/roads` | Roads, segments, zones |
| Geography | `/api/v1/geography` | Provinces, districts, municipalities |
| Observations | `/api/v1/observations` | Vehicle observations |
| Plates | `/api/v1/plates` | Plate observations |
| Speeds | `/api/v1/speeds` | Speed measurements |
| Violations | `/api/v1/violations` | Violations, review, evidence |
| Alerts | `/api/v1/alerts` | Alerts, acknowledge, resolve |
| Analytics | `/api/v1/analytics` | Dashboard, charts |
| Audit | `/api/v1/audit-logs` | Audit trail |
| System | `/api/v1/system` | Health, configuration |
| Actuator | `/actuator` | Spring Boot Actuator |

## Default Credentials

| Username | Password | Role |
|----------|----------|------|
| admin | admin123 | SUPER_ADMIN |
| operator | operator123 | SURVEILLANCE_OPERATOR |

## License

Proprietary — All rights reserved.
# road-eye
