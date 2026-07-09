import asyncio
import base64
import cv2
import logging
import time
import numpy as np
from datetime import datetime, timezone
from typing import Dict, Optional, List

from src.config import Settings
from src.common.event_publisher import EventPublisher
from src.common.models import Detection, Track, PlateResult, SpeedMeasurement, ViolationCandidate, HelmetResult
from src.detection.vehicle_detector import VehicleDetector
from src.detection.helmet_detector import HelmetDetector
from src.tracking.tracker import VehicleTracker
from src.speed.speed_estimator import SpeedEstimator
from src.plate.plate_recognizer import PlateRecognizer
from src.violation.violation_detector import ViolationDetector

logger = logging.getLogger(__name__)

COLORS = [
    (255, 0, 0), (0, 255, 0), (0, 0, 255), (255, 255, 0),
    (255, 0, 255), (0, 255, 255), (128, 255, 0), (255, 128, 0),
    (128, 0, 255), (0, 128, 255), (255, 128, 128), (128, 255, 128),
]

RECONNECT_BASE_DELAY = 1.0
RECONNECT_MAX_DELAY = 30.0
RECONNECT_MAX_ATTEMPTS = 10
CAP_PROP_BUFFER_SIZE = 2


class VideoProcessor:
    def __init__(self, camera_id: str, source_url: str, target_fps: int,
                 event_publisher: EventPublisher, settings: Settings):
        self.camera_id = camera_id
        self.source_url = source_url
        self.target_fps = target_fps
        self.event_publisher = event_publisher
        self.settings = settings
        self.running = False
        self.error = False
        self.error_message: Optional[str] = None
        self.current_fps = 0
        self.vehicles_detected = 0
        self.started_at: Optional[datetime] = None
        self.frame_count = 0
        self.dropped_frames = 0
        self.frame_ready = False

        # Startup timing
        self._timing: dict = {}
        self._mark_timing("PROCESSOR_CREATED")

        self.latest_frame: Optional[np.ndarray] = None
        self.latest_frame_lock = asyncio.Lock()
        # Store one raw (non-annotated) frame for evidence capture
        self.latest_raw_frame: Optional[np.ndarray] = None

        self.vehicle_detector = VehicleDetector(settings)
        self._mark_timing("DETECTOR_INITIALIZED")

        self.tracker = VehicleTracker(
            max_lost=settings.TRACK_MAX_LOST,
            iou_threshold=settings.TRACK_MATCH_THRESHOLD,
            min_hits=settings.TRACK_MIN_HITS,
        )
        self.speed_estimator = SpeedEstimator()
        self.plate_recognizer = PlateRecognizer(settings)
        self.helmet_detector = HelmetDetector(
            model_path=settings.HELMET_MODEL_PATH,
            conf_threshold=settings.HELMET_DETECTION_CONFIDENCE,
        ) if settings.HELMET_DETECTION_ENABLED else None
        self.violation_detector = ViolationDetector(
            detector_version=f"yolov8-{self.vehicle_detector.model_version}",
            helmet_temporal_window=settings.HELMET_TEMPORAL_WINDOW,
        )
        self._mark_timing("ALL_COMPONENTS_INITIALIZED")

        self._track_speeds: dict[int, float] = {}
        self._track_plates: dict[int, str] = {}
        self._track_plate_conf: dict[int, float] = {}
        self._track_ocr_counters: dict[int, int] = {}
        self._published_tracks: set[int] = set()
        self._published_plates: set[int] = set()
        self._track_publish_counter: dict[int, int] = {}
        self._speed_publish_counter: dict[int, int] = {}
        self._track_detection_counter: dict[int, int] = {}
        self._frame_counter = 0
        self._violation_publish_counter: dict[str, int] = {}
        self._helmet_results_cache: Dict[int, List[HelmetResult]] = {}
        self._last_cleanup_time = time.time()
        self._inference_latency_ms: float = 0.0
        self._detection_enabled_frame_count = 0  # counts frames where detection is attempted

    def _mark_timing(self, label: str):
        self._timing[label] = time.time()

    def _log_timing(self):
        if not self._timing:
            return
        items = list(self._timing.items())
        total = items[-1][1] - items[0][1]
        logger.info(f"=== Startup timing for {self.camera_id} ===")
        for i, (label, ts) in enumerate(items):
            if i == 0:
                logger.info(f"  {label}: 0ms")
            else:
                delta = (ts - items[0][1]) * 1000
                logger.info(f"  {label}: +{delta:.0f}ms")
        logger.info(f"  TOTAL: {total * 1000:.0f}ms")
        logger.info(f"  ===============================")

    async def _connect_source(self) -> Optional[cv2.VideoCapture]:
        self._mark_timing("RTSP_CONNECTION_STARTED")
        cap = cv2.VideoCapture(self.source_url)
        if cap.isOpened():
            self._mark_timing("RTSP_CONNECTION_ESTABLISHED")
            cap.set(cv2.CAP_PROP_BUFFERSIZE, CAP_PROP_BUFFER_SIZE)
            return cap
        cap.release()
        return None

    async def run(self):
        self.running = True
        self.started_at = datetime.now(timezone.utc)
        self._mark_timing("RUN_CALLED")

        cap = cv2.VideoCapture(self.source_url)
        if not cap.isOpened():
            logger.error(f"Failed to open video source for camera {self.camera_id}")
            await self._publish_health_event("OFFLINE", "Failed to open video source")
            self.running = False
            self.error = True
            self.error_message = "Unable to connect to camera stream"
            return
        cap.set(cv2.CAP_PROP_BUFFERSIZE, CAP_PROP_BUFFER_SIZE)
        self._mark_timing("RTSP_CONNECTION_ESTABLISHED")

        # Log the actual capture resolution
        actual_w = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
        actual_h = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
        actual_fps = cap.get(cv2.CAP_PROP_FPS)
        logger.info(f"Camera {self.camera_id}: capture res={actual_w}x{actual_h} FPS={actual_fps:.1f}")

        frame_interval = 1.0 / self.target_fps
        last_frame_time = 0
        last_health_publish = 0
        reconnect_attempt = 0
        reconnect_delay = RECONNECT_BASE_DELAY
        prev_frame_time = time.time()

        while self.running:
            if cap is None:
                if reconnect_attempt >= RECONNECT_MAX_ATTEMPTS:
                    logger.error(f"Max reconnection attempts reached for {self.camera_id}")
                    self.error = True
                    self.error_message = "Max reconnection attempts reached"
                    await self._publish_health_event("OFFLINE", "Max reconnection attempts reached")
                    break
                reconnect_attempt += 1
                logger.info(f"Reconnecting {self.camera_id} attempt {reconnect_attempt}/{RECONNECT_MAX_ATTEMPTS} "
                             f"in {reconnect_delay:.0f}s")
                await asyncio.sleep(reconnect_delay)
                cap = await self._connect_source()
                if cap is not None:
                    reconnect_attempt = 0
                    reconnect_delay = RECONNECT_BASE_DELAY
                    cap.set(cv2.CAP_PROP_BUFFERSIZE, CAP_PROP_BUFFER_SIZE)
                    self._mark_timing("RTSP_RECONNECTED")
                else:
                    reconnect_delay = min(reconnect_delay * 2, RECONNECT_MAX_DELAY)
                continue

            loop_start = time.time()
            ret, frame = cap.read()

            if not ret:
                logger.warning(f"Failed to read frame from {self.camera_id}")
                self.dropped_frames += 1
                cap.release()
                cap = None
                reconnect_delay = RECONNECT_BASE_DELAY
                continue

            reconnect_attempt = 0
            self.frame_count += 1
            self._frame_counter += 1
            now = time.time()
            dt_actual = now - prev_frame_time
            prev_frame_time = now

            if self.frame_count == 1:
                self._mark_timing("FIRST_FRAME_CAPTURED")

            # Throttle to target FPS
            if now - last_frame_time < frame_interval:
                continue
            last_frame_time = now

            # Only resize if configured to a non-zero size; otherwise keep original
            if self.settings.FRAME_WIDTH > 0 and self.settings.FRAME_HEIGHT > 0:
                process_frame = cv2.resize(frame, (self.settings.FRAME_WIDTH, self.settings.FRAME_HEIGHT))
            else:
                process_frame = frame.copy() if frame is not None else frame

            # Store a raw (non-annotated) copy for evidence
            raw_for_evidence = process_frame.copy()

            frame_rgb = cv2.cvtColor(process_frame, cv2.COLOR_BGR2RGB)

            publish_interval = max(1, int(self.target_fps / 2))
            should_publish = (self._frame_counter % publish_interval == 0)

            inference_start = time.time()
            try:
                # Frame-skipped detection: run YOLO every DETECTION_FRAME_SKIP frames
                detections = []
                detect_this_frame = (self._frame_counter % self.settings.DETECTION_FRAME_SKIP == 1)
                if detect_this_frame:
                    detections = self.vehicle_detector.detect(frame_rgb)
                    if detections:
                        d = detections[0]
                        logger.info(f"[DETECTION] frame={self._frame_counter} raw_dets={len(detections)} "
                                     f"class={d.class_name}(id={d.class_id}) conf={d.confidence:.4f} "
                                     f"bbox=[{d.bbox[0]:.0f},{d.bbox[1]:.0f},{d.bbox[2]:.0f},{d.bbox[3]:.0f}] "
                                     f"mapped={d.mapped_category}")
                    self._detection_enabled_frame_count += 1
                    if self.frame_count == 1:
                        self._mark_timing("FIRST_DETECTION_COMPLETED")
                else:
                    logger.debug(f"[SKIP] frame={self._frame_counter} detections skipped, "
                                  f"active_tracks={len(self.tracker._states)}")

                # Update tracker with current detections (or empty on skip frames)
                tracks = self.tracker.update(detections, frame_rgb, dt=dt_actual)
                confirmed = self.tracker.get_confirmed_tracks()

                if confirmed:
                    c = confirmed[0]
                    logger.info(f"[TRACKER] frame={self._frame_counter} confirmed={len(confirmed)} "
                                 f"states={len(self.tracker._states)} "
                                 f"first_track_id={c.track_id} hits={c.hit_count} cat={c.category}")

                if self.frame_count == 1:
                    self._mark_timing("FIRST_TRACKING_COMPLETED")

                speed_results = self.speed_estimator.estimate(
                    confirmed, frame_interval
                ) if self.settings.SPEED_ESTIMATION_ENABLED else []

                # Per-track OCR scheduling: only OCR tracks that haven't been
                # processed recently
                plate_results = []
                if self.settings.PLATE_RECOGNITION_ENABLED and confirmed:
                    for trk in confirmed:
                        tid = trk.track_id
                        self._track_ocr_counters[tid] = self._track_ocr_counters.get(tid, 0) + 1
                        ocr_interval = self.settings.OCR_TRACK_INTERVAL
                        if tid not in self._track_plates or self._track_ocr_counters[tid] >= ocr_interval:
                            if tid in self._track_plates:
                                self._track_ocr_counters[tid] = 0
                            track_plates = self.plate_recognizer.recognize_single(frame_rgb, trk)
                            if track_plates:
                                plate_results.extend(track_plates)

                # Helmet detection on motorcycle/scooter tracks
                helmet_results: Dict[int, List[HelmetResult]] = {}
                if self.helmet_detector is not None and self.settings.HELMET_DETECTION_ENABLED:
                    rider_tracks = [
                        (t.track_id, t.detections[-1].bbox if t.detections else None,
                         t.category.value if t.category else "")
                        for t in confirmed
                        if t.detections
                    ]
                    rider_tracks_filtered = [
                        (tid, bbox, cat) for tid, bbox, cat in rider_tracks
                        if bbox is not None
                    ]
                    if rider_tracks_filtered:
                        helmet_results = self.helmet_detector.detect_riders(frame_rgb, rider_tracks_filtered)
                    self._helmet_results_cache = helmet_results

                violations = self.violation_detector.detect(
                    confirmed, speed_results, plate_results, camera_id=self.camera_id,
                    helmet_results=helmet_results if helmet_results else None,
                ) if self.settings.VIOLATION_DETECTION_ENABLED else []

                await self._publish_detections(confirmed, should_publish)
                await self._publish_plates(plate_results, should_publish)
                await self._publish_speeds(speed_results, should_publish)
                await self._publish_violations(violations)
                self._inference_latency_ms = (time.time() - inference_start) * 1000

                self.vehicles_detected = len(confirmed)
                if self.vehicles_detected > 0 and self._frame_counter % 30 == 0:
                    logger.info(f"[STATS] camera={self.camera_id} frame={self._frame_counter} "
                                 f"vehicles={self.vehicles_detected} tracks={len(self.tracker._states)} "
                                 f"published={len(self._published_tracks)} fps={self.current_fps:.1f}")

                for s in speed_results:
                    self._track_speeds[s.track_id] = s.speed_kmh
                for p in plate_results:
                    if p.track_id is not None:
                        self._track_plates[p.track_id] = p.normalized_plate
                        self._track_plate_conf[p.track_id] = p.ocr_confidence

                annotated = self._draw_annotations(process_frame, tracks, detections, plate_results, speed_results, violations)
                async with self.latest_frame_lock:
                    self.latest_frame = annotated
                    self.latest_raw_frame = raw_for_evidence

                if not self.frame_ready:
                    self.frame_ready = True
                    self._mark_timing("FIRST_ANNOTATED_FRAME_READY")
                    self._log_timing()

            except Exception as e:
                logger.error(f"Processing error for {self.camera_id}: {e}", exc_info=True)
                self.dropped_frames += 1

            if now - last_health_publish > 30:
                last_health_publish = now
                await self._publish_health_event("ONLINE", "Processing")

            if now - self._last_cleanup_time > 60:
                self._last_cleanup_time = now
                self._cleanup_publish_state()

            elapsed = time.time() - loop_start
            sleep_time = max(0, frame_interval - elapsed)
            if sleep_time > 0:
                await asyncio.sleep(sleep_time)

            self.current_fps = 1.0 / max(elapsed, 0.001)

        if cap is not None:
            cap.release()
        logger.info(f"Video processor stopped for camera {self.camera_id}")

    def set_calibration(self, known_distance_m: float, reference_pixels: float,
                        calibration_data: Optional[str] = None):
        self.speed_estimator.calibrate(known_distance_m, reference_pixels, calibration_data)
        logger.info(f"Calibration set for camera {self.camera_id}: "
                     f"distance={known_distance_m}m, pixels={reference_pixels}px")

    async def stop(self):
        self.running = False
        logger.info(f"Stopping video processor for camera {self.camera_id}")

    async def get_latest_frame(self) -> Optional[np.ndarray]:
        async with self.latest_frame_lock:
            if self.latest_frame is None:
                return None
            return self.latest_frame.copy()

    def _draw_annotations(self, frame: np.ndarray, tracks: List[Track],
                          detections: List[Detection], plate_results: List[PlateResult],
                          speed_results: List[SpeedMeasurement],
                          violations: List[ViolationCandidate]) -> np.ndarray:
        annotated = frame.copy()
        h, w = annotated.shape[:2]
        font = cv2.FONT_HERSHEY_SIMPLEX

        confirmed_tracks = [t for t in tracks if t.confirmed]

        # Base font settings adaptive to frame width
        font_scale = max(0.45, min(0.7, w / 1500))
        thickness = max(1, int(w / 700))

        for i, track in enumerate(confirmed_tracks):
            if not track.detections:
                continue
            det = track.detections[-1]
            bbox = det.bbox
            color = COLORS[track.track_id % len(COLORS)]

            x1, y1, x2, y2 = map(int, bbox)
            cv2.rectangle(annotated, (x1, y1), (x2, y2), color, max(1, int(w / 600)))

            # Build overlay: show only what we actually know
            label_parts = [f"ID {track.track_id}"]

            speed = self._track_speeds.get(track.track_id)
            if speed is not None and speed > 0:
                label_parts.append(f"{speed:.0f} km/h")

            plate = self._track_plates.get(track.track_id)
            if plate:
                label_parts.append(plate)

            # Helmet status for rider vehicles
            helmet_info = self._helmet_results_cache.get(track.track_id)
            if helmet_info:
                has_helmet = any(r.has_helmet for r in helmet_info)
                if has_helmet:
                    label_parts.append("HELMET")
                else:
                    label_parts.append("NO HELMET")
                    color = (0, 0, 255)

            # Check if this track is in a violation for this frame
            violating = any(v.track_id == track.track_id for v in violations)
            if violating:
                label_parts.append("VIOLATION")
                color = (0, 0, 255)  # Red for violation tracks

            label = " | ".join(label_parts)

            # Draw label background with padding
            (tw, th), _ = cv2.getTextSize(label, font, font_scale, thickness)
            lx, ly = x1, max(y1 - 10, 5)
            cv2.rectangle(annotated, (lx, ly - th - 4), (lx + tw + 6, ly + 4), (0, 0, 0), -1)
            cv2.putText(annotated, label, (lx + 3, ly - 2), font, font_scale, color, thickness)

        # Violation banner at top of frame
        for violation in violations:
            cv2.putText(annotated, f"VIOLATION: {violation.violation_type.value}",
                        (10, 60), font, 0.7, (0, 0, 255), 2)

            if violation.track_id:
                for track in tracks:
                    if track.track_id == violation.track_id and track.detections:
                        bbox = track.detections[-1].bbox
                        x1, y1, x2, y2 = map(int, bbox)
                        cv2.rectangle(annotated, (x1, y1), (x2, y2), (0, 0, 255), max(2, int(w / 400)))

        # Top status bar
        status_lines = []
        status_lines.append(f"FPS: {self.current_fps:.1f} | Tracks: {len(confirmed_tracks)}")

        speed_status = self.speed_estimator.diagnostic_status()
        if speed_status == "SPEED_AVAILABLE":
            status_lines.append("Speed: Calibrated")
        else:
            status_lines.append("Speed: Not Calibrated")

        if self.settings.ENABLE_DIAGNOSTIC_MODE:
            status_lines.append(f"Det: {self._detection_enabled_frame_count}/{self.frame_count}")
            status_lines.append(f"Lat: {self._inference_latency_ms:.0f}ms")
            status_lines.append(f"Dropped: {self.dropped_frames}")
            status_lines.append(f"Res: {w}x{h}")

        status_text = " | ".join(status_lines)

        status_color = (0, 255, 0) if speed_status == "SPEED_AVAILABLE" else (255, 255, 255)
        cv2.putText(annotated, status_text, (10, 30), font, 0.45, status_color, 1)

        return annotated

    async def _publish_health_event(self, status: str, message: str):
        await self.event_publisher.publish(
            topic=self.settings.KAFKA_CAMERA_HEALTH_TOPIC,
            payload={
                "status": status,
                "message": message,
                "processed_frames": self.frame_count,
                "dropped_frames": self.dropped_frames,
                "current_fps": self.current_fps,
                "inference_latency_ms": self._inference_latency_ms,
            },
            camera_id=self.camera_id,
        )

    async def _publish_detections(self, tracks, should_publish):
        for track in tracks:
            if not track.confirmed or not track.detections:
                continue

            tid = track.track_id
            is_new = tid not in self._published_tracks
            if is_new:
                self._published_tracks.add(tid)
                self._track_publish_counter[tid] = 0

            self._track_publish_counter[tid] = self._track_publish_counter.get(tid, 0) + 1
            if not is_new and not should_publish:
                continue

            det = track.detections[-1]
            await self.event_publisher.publish(
                topic=self.settings.KAFKA_VEHICLE_OBSERVATIONS_TOPIC,
                payload={
                    "track_id": tid,
                    "category": track.category.value if track.category else None,
                    "original_class_name": det.class_name,
                    "original_class_id": det.class_id,
                    "confidence": det.confidence,
                    "direction": track.direction,
                    "bbox": det.bbox,
                    "model_name": self.vehicle_detector.model_name,
                    "model_version": self.vehicle_detector.model_version,
                },
                camera_id=self.camera_id,
                correlation_id=f"track-{tid}",
            )

    async def _publish_plates(self, plate_results, should_publish: bool = True):
        for plate in plate_results:
            # Throttle: skip non-first publications between intervals
            tid = plate.track_id
            if tid is not None:
                is_new = tid not in self._published_plates
                if not is_new and not should_publish:
                    continue
                self._published_plates.add(tid)
            await self.event_publisher.publish(
                topic=self.settings.KAFKA_PLATE_RECOGNITIONS_TOPIC,
                payload={
                    "plate_text": plate.plate_text,
                    "normalized_plate": plate.normalized_plate,
                    "country": plate.country,
                    "country_confidence": plate.country_confidence,
                    "ocr_confidence": plate.ocr_confidence,
                    "quality_score": plate.quality_score,
                    "track_id": plate.track_id,
                    "bbox": plate.bbox,
                    "alternatives": plate.alternatives,
                    "detector_version": plate.detector_version,
                    "ocr_model_version": plate.ocr_model_version,
                },
                camera_id=self.camera_id,
            )

    async def _publish_speeds(self, speed_results, should_publish):
        for speed in speed_results:
            tid = speed.track_id
            self._speed_publish_counter[tid] = self._speed_publish_counter.get(tid, 0) + 1
            if self._speed_publish_counter[tid] > 1 and not should_publish:
                continue
            await self.event_publisher.publish(
                topic=self.settings.KAFKA_SPEED_ESTIMATIONS_TOPIC,
                payload={
                    "track_id": tid,
                    "speed_kmh": speed.speed_kmh,
                    "smoothed_speed_kmh": speed.smoothed_speed_kmh,
                    "confidence": speed.confidence,
                    "distance_m": speed.distance_m,
                    "duration_ms": speed.duration_ms,
                    "algorithm_version": speed.algorithm_version,
                    "calibration_version": speed.calibration_version,
                },
                camera_id=self.camera_id,
                correlation_id=f"track-{tid}",
            )

    async def _publish_violations(self, violations):
        for violation in violations:
            dedup_key = f"{violation.violation_type.value}:{violation.track_id}"
            self._violation_publish_counter[dedup_key] = self._violation_publish_counter.get(dedup_key, 0) + 1
            # Throttle: publish first occurrence, then at most once per ~30 frames
            if self._violation_publish_counter[dedup_key] > 1 and self._frame_counter % 30 != 0:
                continue
            payload = violation.model_dump()
            # Capture evidence from raw (non-annotated) frame for clean evidence storage
            async with self.latest_frame_lock:
                evidence_source = self.latest_raw_frame if self.latest_raw_frame is not None else self.latest_frame
                if evidence_source is not None:
                    quality = self.settings.EVIDENCE_QUALITY
                    _, buf = cv2.imencode(".jpg", evidence_source, [cv2.IMWRITE_JPEG_QUALITY, quality])
                    payload["evidence_frames"] = [base64.b64encode(buf).decode("utf-8")]
            await self.event_publisher.publish(
                topic=self.settings.KAFKA_VIOLATION_CANDIDATES_TOPIC,
                payload=payload,
                camera_id=self.camera_id,
                correlation_id=f"violation-{violation.violation_type.value}-{violation.track_id}-{datetime.now(timezone.utc).timestamp()}",
            )

    def _cleanup_publish_state(self):
        active_track_ids = {t.track_id for t in self.tracker.get_confirmed_tracks()}
        stale_tids = [tid for tid in self._published_tracks if tid not in active_track_ids]
        for tid in stale_tids:
            self._published_tracks.discard(tid)
            self._published_plates.discard(tid)
            self._track_speeds.pop(tid, None)
            self._track_plates.pop(tid, None)
            self._track_plate_conf.pop(tid, None)
            self._track_ocr_counters.pop(tid, None)
            self._track_publish_counter.pop(tid, None)
            self._speed_publish_counter.pop(tid, None)
            self._track_detection_counter.pop(tid, None)
        stale_violations = [k for k in self._violation_publish_counter
                            if not any(t.track_id == int(k.split(":")[-1]) for t in self.tracker.get_confirmed_tracks()
                                       if k.split(":")[-1].isdigit())]
        for k in stale_violations:
            self._violation_publish_counter.pop(k, None)
        self.speed_estimator.cleanup(active_track_ids)
        self.violation_detector.cleanup_helmet_state(self.camera_id, active_track_ids)
        # Clean up cached helmet results
        stale_helmets = [tid for tid in self._helmet_results_cache if tid not in active_track_ids]
        for tid in stale_helmets:
            self._helmet_results_cache.pop(tid, None)
        # Clean up PlateRecognizer OCR history for expired tracks
        self.plate_recognizer.cleanup_track_history(active_track_ids)
        if stale_tids or stale_violations:
            logger.debug(f"Cleaned up publish state for camera {self.camera_id}: "
                         f"{len(stale_tids)} tracks, {len(stale_violations)} violations")
