# MJPEG Streaming Fix — Final Report

## 1. ROOT CAUSE: `AsyncRequestTimeoutException`

**Issue**: The `proxyMjpeg` endpoint in `CameraController.java:228` uses Spring MVC's `StreamingResponseBody`, which relies on the Servlet 3.0 async request processing mechanism. When no explicit async timeout is configured, the underlying Tomcat container applies its **default async timeout of 30 seconds**.

**Why**: 
- `StreamingResponseBody` is an async return type in Spring MVC.
- Spring MVC's `AsyncSupportConfigurer` was never configured — it was absent from `WebConfig.java`.
- `spring.mvc.async.request-timeout` was not set in `application.yml`.
- Tomcat's `AsyncContext.setTimeout()` was never called, so the default 30-second timeout applied.
- After ~30 seconds, the timeout fired, Spring MVC detected the expired async request, and threw `AsyncRequestTimeoutException`.

## 2. SECONDARY FAILURE: `HttpMessageNotWritableException`

**Issue**: The `GlobalExceptionHandler.handleGeneric(Exception)` method caught the `AsyncRequestTimeoutException` and attempted to return `ResponseEntity<ApiResponse<Void>>` as JSON. However, by the time the timeout fired, the HTTP response already had:

- **Content-Type**: `multipart/x-mixed-replace; boundary=frame` (set at line 244 of CameraController.java)
- **Status**: potentially partially committed (streaming data already written)

Spring's `HttpMessageConverter` infrastructure could not find a converter capable of serializing `ApiResponse` into `multipart/x-mixed-replace`, causing `HttpMessageNotWritableException`.

**Why**: The `@ExceptionHandler(Exception.class)` handler at line 75 of `GlobalExceptionHandler.java` was a blanket catch-all that always returned `ApiResponse` as JSON, without checking if the response content type or commit state was compatible.

## 3. REQUEST FLOW

```
Browser <img src=".../mjpeg">
  │
  ▼
GET /api/v1/cameras/{id}/stream/mjpeg
  │
  ├── CameraController.proxyMjpeg()
  │     ├── Creates StreamingResponseBody lambda
  │     ├── Sets Content-Type: multipart/x-mixed-replace
  │     └── Returns ResponseEntity<StreamingResponseBody>
  │
  ├── Spring MVC async processing starts
  │     ├── Submits StreamingResponseBody as Callable to async executor
  │     └── Tomcat async context started (with default 30s timeout if not configured)
  │
  ├── StreamingResponseBody lambda executes
  │     ├── Opens InputStream from http://localhost:8000/.../mjpeg  (AI Engine)
  │     ├── Calls in.transferTo(outputStream)  ← copies frames to browser
  │     └── Each frame: --frame\r\nContent-Type: image/jpeg\r\n...jpeg data...
  │
  ├── [~30 seconds later if no fix] Async timeout expires
  │     ├── AsyncRequestTimeoutException thrown
  │     ├── GlobalExceptionHandler.handleGeneric() caught it
  │     └── Attempted to write JSON ApiResponse with MJPEG Content-Type → FAILED
  │
  └── [WITH FIX] Async task continues indefinitely
        ├── Client disconnect → IOException on outputStream.write()
        ├── Caught in proxyMjpeg lambda, logged at DEBUG, returns gracefully
        └── AI Engine stream ends → in.transferTo returns normally
```

## 4. FILES CHANGED

| File | What Changed | Why |
|------|-------------|-----|
| `backend/src/main/resources/application.yml:47-49` | Added `spring.mvc.async.request-timeout: 0` under `spring.mvc.*` | Disables the Tomcat async timeout for streaming responses. 0 means "no timeout" |
| `backend/.../shared/GlobalExceptionHandler.java:3,16,77-85,87-96` | Added `HttpServletResponse` import, `AsyncRequestTimeoutException` import, new handler for `AsyncRequestTimeoutException`, modified `handleGeneric` with `response.isCommitted()` check | Prevents JSON serialization into MJPEG responses; handles steaming lifecycle errors gracefully |
| `backend/.../camera/controller/CameraController.java:37,234-241` | Added `java.io.IOException` import, added catch block in `proxyMjpeg` lambda | Catches client disconnect (Broken pipe) and upstream stream closure at the source, logs at appropriate level, prevents exception propagation |

## 5. EXACT FIX

### Fix A: `application.yml` — Disable async timeout

```yaml
spring:
  mvc:
    async:
      request-timeout: 0
```

**Rationale**: `spring.mvc.async.request-timeout` controls Spring MVC's async request timeout (only affects async return types like `StreamingResponseBody`, `DeferredResult`, `Callable`). Setting to `0` disables the timeout. This does NOT affect:
- Regular synchronous REST endpoints (they don't use async processing)
- `@Async` annotated methods (they use `TaskExecutor`, not async request processing)
- Any other HTTP request handling

### Fix B: `GlobalExceptionHandler.java` — Streaming-safe error handling

1. **Specific handler for `AsyncRequestTimeoutException`**: Checks `response.isCommitted()`. If committed (normal streaming scenario), just logs at DEBUG and returns silently (no body written). If not committed (edge case), sets status to `503 SERVICE_UNAVAILABLE`.

2. **`handleGeneric` now takes `HttpServletResponse` and checks `isCommitted()`**: If the response is already committed (streaming in progress), logs the error but returns `null` (no response body attempted). This prevents the `HttpMessageNotWritableException`. Normal REST API exceptions where the response is not committed still return `ApiResponse` as before.

### Fix C: `CameraController.java` — Client disconnect handling

The `proxyMjpeg` lambda now catches `IOException`:
- **Broken pipe / Connection reset / stream closed**: Logged at DEBUG level (expected during normal browser navigation)
- **Other IOExceptions**: Logged at WARN level (potential upstream or network issue)
- In all cases, the lambda returns gracefully — no exception propagates to Spring MVC's error handling

## 6. STREAM LIFECYCLE

**Opening**:
1. Browser sets `<img src="/api/v1/cameras/{id}/stream/mjpeg">`
2. Spring Boot receives GET request
3. `proxyMjpeg()` creates `StreamingResponseBody`, sets MJPEG Content-Type
4. Spring MVC starts async processing (no timeout with fix)
5. Lambda opens `InputStream` to AI Engine MJPEG endpoint
6. Frames are copied from AI Engine to browser via `in.transferTo(outputStream)`

**Long-running streaming**:
- No async timeout (configured to 0)
- Frames stream continuously as long as both connections are alive
- Each frame is a multipart boundary with JPEG image data

**Client disconnect (browser navigation, refresh, tab close)**:
1. `outputStream.write()` throws `IOException` (Broken pipe or Connection reset)
2. Caught in lambda's catch block
3. Logged at DEBUG level: `"MJPEG stream closed for camera {id}: {message}"`
4. Lambda returns normally
5. `try-with-resources` closes the upstream `InputStream` (AI Engine connection)
6. Spring MVC completes the async request normally (no error)

**AI Engine disconnect (camera offline, AI Engine crash)**:
1. `in.read()` returns -1 or throws IOException
2. If -1: `transferTo` returns normally, `outputStream.flush()` succeeds
3. If IOException: caught in catch block, logged at WARN
4. Lambda returns normally
5. Browser receives end of stream and shows last frame or broken image icon

**Camera stop (intentional via UI)**:
1. Spring Boot `POST /stream/stop` → AI Engine stops processor
2. AI Engine MJPEG generator stops producing frames
3. Upstream InputStream completes/throws
4. Same handling as AI Engine disconnect above

## 7. MULTIPLE CAMERA SAFETY

Each camera's MJPEG stream is an independent HTTP request-response:
- `StreamingResponseBody` is created per-request
- Each request opens its own `InputStream` to the AI Engine
- One camera's stream failure does not affect others
- Each lambda has its own catch block and lifecycle
- The fix scales to N cameras × M viewers naturally

The `key={id}` on `LiveCameraCard` components ensures React correctly manages multiple independent component instances.

## 8. TEST RESULTS

**Environment limitation**: No build tools (`java`, `mvn`, `node`, `npm`, `python`) are available in this environment. The following tests MUST be run manually after deploying to the target machine:

### TEST 1 — Backend Compilation
```bash
cd backend && mvn clean compile
# Expected: BUILD SUCCESS
```

### TEST 2 — Frontend Build
```bash
cd frontend && npm run build
# Expected: Build succeeds, 938KB bundle
```

### TEST 3 — AI Engine Validation
```bash
cd ai-engine && python -c "import py_compile; py_compile.compile('src/main.py', doraise=True)"
# Expected: No syntax errors
```

### TEST 4 — Long-Running MJPEG
1. Start camera stream
2. Open Live Monitoring page
3. Keep stream open for 5+ minutes
4. Expected: No `AsyncRequestTimeoutException`, no `HttpMessageNotWritableException`, stream remains visible

### TEST 5 — Navigation
1. Open stream, navigate Live Monitoring → Violations → Alerts → Live Monitoring
2. Expected: Stream reconnects each time, no orphan connections, no backend exceptions

### TEST 6 — Browser Refresh
1. Refresh page during streaming
2. Expected: Old connection closes cleanly (`"MJPEG stream closed"` at DEBUG), new connection opens

### TEST 7 — Stop Camera
1. Click Stop in Live Monitoring card
2. Expected: Stream stops, UI updates, no exception serialization failure

### TEST 8 — Camera Failure
1. Make camera RTSP source unavailable
2. Expected: Stream transitions to ERROR state, no server crash

## 9. LONG-RUN TEST RESULT

Could not execute in this environment (no runtime tools available). The fix is designed to eliminate the 30-second timeout entirely, so the stream should remain connected indefinitely as long as both browser and AI Engine are alive.

## 10. REMAINING ISSUES

1. **No build tools in environment**: Cannot compile or run to verify. All changes are syntactically verified by inspection.

2. **MJPEG endpoints unauthenticated**: `SecurityConfig.java` permits all for `/api/v1/cameras/*/stream/mjpeg`. Documented but not fixed (out of scope — this is a pre-existing design decision).

3. **Possible thread exhaustion with many MJPEG viewers**: Each `StreamingResponseBody` uses one Tomcat request processing thread for the duration of the connection. With many concurrent viewers, this could exhaust the thread pool. Mitigation: Spring Boot's default thread pool handles this reasonably for moderate viewer counts. For high-scale deployments, consider Spring WebFlux or direct-to-AI-Engine connections.

4. **Frontend `_=${Date.now()}` cache-busting**: Creates a new URL on each `streamActive`/`cameraId` change. This is intentional and correct for cache prevention, but ensures a fresh connection on state change.

5. **No backpressure from AI Engine MJPEG generator**: The `generate_frames()` in `main.py:211` runs at 20 FPS regardless of whether the downstream consumer is keeping up. With the Spring Boot proxy, this is mitigated by the InputStream buffer. At very high frame rates or slow networks, this could cause memory pressure. Acceptable for current use case.
