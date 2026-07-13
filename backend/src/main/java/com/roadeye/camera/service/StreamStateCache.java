package com.roadeye.camera.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Backend-owned in-memory cache of camera stream status.
 * Updated by direct commands (start/stop) and Kafka health events.
 * Eliminates synchronous blocking calls to AI Engine for status queries.
 */
@Component
public class StreamStateCache {

    private static final Logger log = LoggerFactory.getLogger(StreamStateCache.class);

    private final ConcurrentHashMap<String, StreamState> states = new ConcurrentHashMap<>();

    public enum Status {
        STOPPED, STARTING, CONNECTING, RUNNING, RECONNECTING, DEGRADED, ERROR, OFFLINE
    }

    public static class StreamState {
        public final String cameraId;
        public volatile Status status;
        public volatile double fps;
        public volatile int vehiclesDetected;
        public volatile int frameCount;
        public volatile int droppedFrames;
        public volatile boolean frameReady;
        public volatile Instant startedAt;
        public volatile Instant lastHeartbeat;
        public volatile String errorMessage;
        public volatile Instant updatedAt;

        public StreamState(String cameraId, Status status) {
            this.cameraId = cameraId;
            this.status = status;
            this.updatedAt = Instant.now();
        }

        public Map<String, Object> toMap() {
            Map<String, Object> stream = new HashMap<>();
            stream.put("status", status.name());
            stream.put("fps", fps);
            stream.put("vehicles_detected", vehiclesDetected);
            stream.put("frame_count", frameCount);
            stream.put("dropped_frames", droppedFrames);
            stream.put("frame_ready", frameReady);
            if (startedAt != null) stream.put("started_at", startedAt.toString());
            if (lastHeartbeat != null) stream.put("last_heartbeat", lastHeartbeat.toString());
            if (errorMessage != null) stream.put("error_message", errorMessage);

            Map<String, Object> result = new HashMap<>();
            result.put("camera_id", cameraId);
            result.put("stream", stream);
            return result;
        }
    }

    public void markStarting(String cameraId) {
        StreamState state = new StreamState(cameraId, Status.STARTING);
        states.put(cameraId, state);
        log.debug("Stream state -> STARTING for camera {}", cameraId);
    }

    public void markConnecting(String cameraId) {
        StreamState state = states.get(cameraId);
        if (state != null) {
            state.status = Status.CONNECTING;
            state.updatedAt = Instant.now();
        } else {
            states.put(cameraId, new StreamState(cameraId, Status.CONNECTING));
        }
        log.debug("Stream state -> CONNECTING for camera {}", cameraId);
    }

    public void markRunning(String cameraId) {
        markRunning(cameraId, null);
    }

    public void markRunning(String cameraId, Map<String, Object> streamInfo) {
        StreamState state = states.get(cameraId);
        if (state == null) {
            state = new StreamState(cameraId, Status.RUNNING);
            states.put(cameraId, state);
        }
        state.status = Status.RUNNING;
        state.startedAt = Instant.now();
        state.updatedAt = Instant.now();
        if (streamInfo != null) {
            state.fps = getDouble(streamInfo, "fps");
            state.vehiclesDetected = getInt(streamInfo, "vehicles_detected");
            state.frameCount = getInt(streamInfo, "frame_count");
            state.droppedFrames = getInt(streamInfo, "dropped_frames");
            state.frameReady = getBoolean(streamInfo, "frame_ready");
        }
        log.debug("Stream state -> RUNNING for camera {}", cameraId);
    }

    public void markStopped(String cameraId) {
        states.remove(cameraId);
        log.debug("Stream state -> STOPPED for camera {}", cameraId);
    }

    public void markError(String cameraId, String errorMessage) {
        StreamState state = states.get(cameraId);
        if (state != null) {
            state.status = Status.ERROR;
            state.errorMessage = errorMessage;
            state.updatedAt = Instant.now();
        } else {
            StreamState newState = new StreamState(cameraId, Status.ERROR);
            newState.errorMessage = errorMessage;
            states.put(cameraId, newState);
        }
        log.debug("Stream state -> ERROR for camera {}: {}", cameraId, errorMessage);
    }

    public void markReconnecting(String cameraId) {
        StreamState state = states.get(cameraId);
        if (state != null) {
            state.status = Status.RECONNECTING;
            state.updatedAt = Instant.now();
        } else {
            states.put(cameraId, new StreamState(cameraId, Status.RECONNECTING));
        }
    }

    public void updateHeartbeat(String cameraId, String status, String message,
                                 double fps, int vehicles, int frames, int dropped) {
        StreamState state = states.get(cameraId);
        if (state == null) {
            state = new StreamState(cameraId, Status.RUNNING);
            states.put(cameraId, state);
        }
        state.lastHeartbeat = Instant.now();
        state.updatedAt = Instant.now();
        state.fps = fps;
        state.vehiclesDetected = vehicles;
        state.frameCount = frames;
        state.droppedFrames = dropped;

        if ("ONLINE".equals(status) || "RUNNING".equals(status)) {
            state.status = Status.RUNNING;
        } else if ("OFFLINE".equals(status)) {
            state.status = Status.OFFLINE;
        } else if ("ERROR".equals(status)) {
            state.status = Status.ERROR;
            state.errorMessage = message;
        }
    }

    public Optional<StreamState> getState(String cameraId) {
        return Optional.ofNullable(states.get(cameraId));
    }

    public Status getStatus(String cameraId) {
        StreamState state = states.get(cameraId);
        return state != null ? state.status : Status.STOPPED;
    }

    public boolean isRunning(String cameraId) {
        Status status = getStatus(cameraId);
        return status == Status.RUNNING || status == Status.RECONNECTING;
    }

    public Map<String, Object> getFullState(String cameraId) {
        StreamState state = states.get(cameraId);
        if (state == null) {
            Map<String, Object> stream = new HashMap<>();
            stream.put("status", "STOPPED");
            stream.put("fps", 0);
            stream.put("vehicles_detected", 0);
            stream.put("frame_count", 0);
            stream.put("dropped_frames", 0);
            stream.put("frame_ready", false);

            Map<String, Object> result = new HashMap<>();
            result.put("camera_id", cameraId);
            result.put("stream", stream);
            return result;
        }
        return state.toMap();
    }

    private static double getDouble(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.doubleValue();
        return 0.0;
    }

    private static int getInt(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number n) return n.intValue();
        return 0;
    }

    private static boolean getBoolean(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Boolean b) return b;
        return false;
    }
}
