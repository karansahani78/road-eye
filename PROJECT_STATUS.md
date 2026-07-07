# RoadEye — Project Status

## Current Phase: Phase 1 — Foundation (In Progress)

### Completed Functionality

#### Infrastructure
- [x] Docker Compose with PostgreSQL/PostGIS, Kafka, MinIO, Backend, AI Engine, Frontend
- [x] Dockerfiles for all services
- [x] Environment configuration (.env.example, .gitignore)
- [x] Named volumes for data persistence
- [x] Health checks for dependencies

#### Backend (Spring Boot 3.2.4, Java 21, Maven)
- [x] Modular monolith structure with 16 modules
- [x] Database schema (V1-V3 Flyway migrations) with 25+ tables
- [x] Security: JWT auth, BCrypt, CORS, CSRF disabled
- [x] Auth module: Users, Roles, Refresh Tokens
- [x] Camera module: CRUD, calibration, health events
- [x] Geography module: Province, District, Municipality
- [x] Road module: Roads, segments, intersections, zones, speed limits
- [x] Vehicle module: Observations, tracks
- [x] Plate module: Plate observations
- [x] Speed module: Speed measurements
- [x] Violation module: Violations, evidence, reviews, status workflow
- [x] Alert module: Alerts, acknowledge, resolve
- [x] Audit module: Audit logging
- [x] Analytics controller: Dashboard stats, charts data endpoints
- [x] System module: Health, configuration
- [x] Kafka: Producer config, 6 consumer listeners
- [x] WebSocket: STOMP over SockJS for real-time alerts
- [x] MinIO: Client config, bucket initialization
- [x] Global exception handler, unified API responses

#### AI Engine (Python 3.11, FastAPI)
- [x] FastAPI application with health and stream management endpoints
- [x] Video processor: Async frame loop with FPS throttling
- [x] Vehicle detector: YOLOv8 integration with fallback
- [x] Vehicle tracker: IoU-based multi-object tracking
- [x] Speed estimator: Pixel-distance calculation
- [x] Plate recognizer: EasyOCR with Nepal/India/UNKNOWN normalization
- [x] Violation detector: Overspeeding, wrong-way, no-helmet
- [x] Event publisher: Kafka with structured events
- [x] Stream manager: Multi-camera lifecycle management
- [x] Extensible vehicle taxonomy (35+ categories)

#### Frontend (React 18, TypeScript, Vite)
- [x] Login page with authentication
- [x] Dashboard with stats cards
- [x] Camera management (list, create, detail, calibration)
- [x] Violations list and detail with review workflow
- [x] Alerts page with acknowledge/resolve
- [x] Traffic map (Leaflet/OpenStreetMap)
- [x] Analytics page with charts (Recharts)
- [x] Users page with status management
- [x] Audit logs page with filtering
- [x] System health page
- [x] Roads & zones page
- [x] JWT auth with refresh token rotation
- [x] Axios interceptors for token handling
- [x] Protected routes, layout, navigation

### In Progress
- [ ] Backend compilation verification (requires JDK 21 + Maven)
- [ ] Frontend build verification (requires Node.js 20)
- [ ] AI Engine verification (requires Python 3.11)
- [ ] Docker Compose integration test
- [ ] End-to-end pipeline test

### Blocked
- None currently

### Known Issues
- Vehicle detection requires YOLOv8 model download on first run
- EasyOCR requires initial model download on first run
- AI engine uses IoU-based tracking (ByteTrack/BoT-SORT planned)
- Speed estimation requires manual camera calibration
- No RTSP test streams configured
- Some frontend analytics charts show empty data (backend endpoints return empty)

### Technical Debt
- AI engine tests are minimal
- Backend unit tests not yet created
- Frontend component tests not yet created
- No CI/CD pipeline
- No Prometheus/Grafana dashboards
- Camera credentials stored in DB without encryption (logged as plaintext in sourceUrl)
- Some empty backend modules may need additional service logic

### AI Limitations
- YOLOv8n is a generic model; custom training needed for Nepal-specific vehicle subtypes
- No helmet detection model — current NO_HELMET detection is confidence-based heuristic
- EasyOCR may struggle with Devanagari script on Nepalese plates
- Speed estimation is CV-based and not legally certified
- No red-light violation detection (requires traffic signal integration)

### External Dependencies
- PostgreSQL 16 with PostGIS extension
- Apache Kafka (via Confluent images)
- MinIO object storage
- YOLOv8 model (downloaded on first run)
- EasyOCR model (downloaded on first run)

### Deployment Status
- Development environment: Docker Compose (not yet tested)
- Production: Not configured
