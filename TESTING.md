# RoadEye — Testing Guide

## Prerequisites

| Tool | Version | Required For |
|------|---------|-------------|
| Docker & Docker Compose | latest | Infrastructure (Postgres, Kafka, MinIO) |
| Java JDK | 21 | Backend build & run |
| Maven | 3.8+ | Backend build (or use `./mvnw`) |
| Node.js | 20 | Frontend build & dev |
| Python | 3.11 | AI Engine (optional) |

---

## 1. Start Infrastructure

```bash
docker compose up -d postgres kafka minio
```

Verify they're healthy:
```bash
docker compose ps
```

---

## 2. Build & Run Backend

```bash
cd backend
./mvnw clean compile
```

### Run Backend Unit Tests

```bash
./mvnw test
```

The pom.xml includes:
- `spring-boot-starter-test` — JUnit 5, Mockito, Spring Test
- `spring-security-test` — Security test utilities
- `testcontainers` (postgresql + kafka) — Integration test containers

> **Note:** Currently no test classes exist under `src/test/java/`. You can verify the test infrastructure works by adding a simple test, or just confirm the project compiles with `./mvnw clean compile`.

### Start Dev Server (requires infrastructure running)

```bash
# Using local Maven
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Or build and run the JAR
./mvnw package -DskipTests
java -jar target/roadeye-backend-0.1.0.jar
```

Backend runs on `http://localhost:8080`.

---

## 3. Run Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend runs on `http://localhost:3000`.

---

## 4. Default Credentials

| Username | Password | Role |
|----------|----------|------|
| `admin` | `admin123` | SUPER_ADMIN (full access) |
| `operator` | `operator123` | SURVEILLANCE_OPERATOR |

On first startup, Flyway runs `V2__seed_data.sql` which seeds both users.
`DefaultUserPasswordBootstrap` resets their passwords to `admin123` / `operator123`.

---

## 5. Manual Testing Flow

### 5.1 Authentication

1. Open `http://localhost:3000`
2. You should see the **Login Page** with a dark blue gradient background
3. Login with `admin` / `admin123`
4. After login, you should be redirected to the **Dashboard**

### 5.2 Dashboard

5. Verify **8 stat cards** render (Total Cameras, Online, Offline, Active Streams, Vehicles Detected, Active Violations, Pending Reviews, Active Alerts)
6. Expected state: all zeros (no data yet)
7. Click any card — should navigate to the relevant page

### 5.3 Cameras

8. Navigate to **Cameras** via sidebar
9. Click **Add Camera** to open the registration form
10. Fill in the form:
    - Camera Code: `CAM-001`
    - Name: `Test Camera`
    - Source Type: `RTSP`
    - Source URL: `rtsp://192.168.1.100:554/stream1`
    - Location: `Kathmandu`
    - Latitude: `27.7172`
    - Longitude: `85.3240`
    - FPS: `15`
11. Click **Create Camera**
12. Verify the camera appears in the table
13. Click the camera row to open **Camera Detail**
14. On detail page, verify:
    - Camera information renders in a card
    - **Calibration** section — fill in known distance and save calibration
    - **Health Events** section shows (initially empty)
    - **Test Connection** button works
    - **Edit Camera** updates the camera
15. Navigate back to camera list, verify the **Delete** button works

### 5.4 Roads & Zones

16. Navigate to **Roads & Zones** via sidebar
17. Verify 5 tab buttons render: Roads, Segments, Zones, Intersections, Speed Limits
18. Initially all data is empty (no seed data for roads)
19. Verify pagination controls render
20. (To test with data, insert roads via API: `POST /api/v1/roads`)

### 5.5 Violations

21. Navigate to **Violations**
22. Verify empty state shows "No violations found"
23. Test the **status filter** dropdown
24. Click a violation row (requires violation data from AI engine or direct DB insert)

### 5.6 Violation Detail & Review Workflow

25. Navigate to a specific violation
26. Verify the detail view shows:
    - Violation details card (type, camera, timestamp, confidence, speed, plate)
    - Review actions card with status dropdown and comment textarea
    - Evidence section (shows "No evidence available" when empty)
27. Test status transitions:
    - PENDING_REVIEW → UNDER_REVIEW or REJECTED
    - UNDER_REVIEW → CONFIRMED, REJECTED, or NEEDS_ADDITIONAL_REVIEW

### 5.7 Alerts & Real-time

28. Navigate to **Alerts**
29. Verify severity filter works
30. Test **Acknowledge** and **Resolve** buttons
31. **Real-time WebSocket:** With the page open, insert an alert via the backend:
    ```bash
    curl -X POST http://localhost:8080/api/v1/alerts \
      -H "Authorization: Bearer TOKEN" \
      -H "Content-Type: application/json" \
      -d '{"type":"SPEEDING","severity":"HIGH","source":"CAM-001","cameraId":"...","message":"Test alert"}'
    ```
    The alert should appear in real-time on the Alerts page.

### 5.8 Traffic Map

32. Navigate to **Traffic Map**
33. Verify the Leaflet map renders with OpenStreetMap tiles centered on Kathmandu
34. If cameras with coordinates exist, they should appear as markers
35. Click a marker to see the camera popup

### 5.9 Analytics

36. Navigate to **Analytics**
37. Verify 3 chart cards render:
    - Traffic Volume (Line Chart — 7 days)
    - Vehicle Categories (Pie Chart)
    - Violation Trends (Bar Chart — 30 days)
38. Charts will be empty until data exists

### 5.10 User Management (Admin only)

39. Navigate to **Users**
40. Verify the users table shows `admin` and `operator`
41. Test **Activate/Deactivate** toggle on a user

### 5.11 Audit Logs (Admin/Auditor only)

42. Navigate to **Audit Logs**
43. Verify audit log entries render with timestamp, actor, action
44. Test the **action filter** input

### 5.12 System Health

45. Navigate to **System Health**
46. Verify 4 health cards render: Database, Kafka, MinIO, AI Engine
47. Each shows `UP` or `DOWN` status
48. Verify Application Info card shows name and version

---

## 6. API Testing (curl)

### Login

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

Save the `accessToken` from the response. Use it as a Bearer token for subsequent requests:

```bash
TOKEN="<accessToken>"
```

### Test endpoints

```bash
# Get cameras
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/cameras

# Get dashboard stats
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/analytics/dashboard

# Check system health
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/system/health

# Get alerts
curl -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/v1/alerts?page=0&size=10"
```

---

## 7. Testing with Postman / Bruno

1. Import the API endpoints from the project structure (see README.md API Endpoints table)
2. Create a login request to get the JWT token
3. Set up Bearer token as a collection variable
4. Test all CRUD endpoints for each module

---

## 8. AI Engine Testing (Optional)

### Start the AI Engine

```bash
cd ai-engine
pip install -r requirements.txt
uvicorn src.main:app --reload --port 8000
```

### Test health

```bash
curl http://localhost:8000/health
```

### Process a test video

Place a test video at `ai-engine/videos/test.mp4`.

API documentation is available at `http://localhost:8000/docs` (FastAPI Swagger UI).

---

## 9. End-to-End: Full Data Pipeline Test

This test verifies the complete flow: AI detection → Kafka → Backend → Database → Frontend.

### Prerequisites

- All Docker services running
- Backend running on `:8080`
- AI engine running on `:8000`
- Frontend running on `:3000`

### Steps

1. **Register a camera** via the frontend or API
2. **Upload/stream a test video** to the AI engine
3. AI engine detects vehicles → publishes to Kafka `vehicle-observations`
4. Backend consumes `vehicle-observations` → stores in database
5. **Verify** the Dashboard stats update on the frontend
6. If violations are detected → check the Violations page
7. If alerts are generated → check the Alerts page (real-time via WebSocket)

---

## 10. Common Issues

| Symptom | Cause | Fix |
|---------|-------|-----|
| White page on frontend | `sockjs-client` missing `global` polyfill | Add `define: { global: 'globalThis' }` to `vite.config.ts` ✅ already fixed |
| Backend won't start: DB connection | Postgres not running | `docker compose up -d postgres` |
| Backend won't start: Kafka connection | Kafka not running | `docker compose up -d kafka` |
| 401 on API calls | Token expired or invalid | Re-login to get a fresh token |
| Flyway migration fails | Schema mismatch or stale DB | `docker compose down -v && docker compose up -d` |
| Blank chart on Analytics | No data | Insert test data via API |
| Map tiles not loading | OpenStreetMap CDN blocked | Check network / provide an alternative tile URL |
| WebSocket not connecting | SockJS/STOMP error | Check browser console; verify `/ws` endpoint is reachable |
