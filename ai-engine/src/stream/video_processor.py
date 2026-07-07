import asyncio
import cv2
import logging
import time
from datetime import datetime
from typing import Optional

from src.config import Settings
from src.common.event_publisher import EventPublisher
from src.detection.vehicle_detector import VehicleDetector
from src.tracking.tracker import VehicleTracker
from src.speed.speed_estimator import SpeedEstimator
from src.plate.plate_recognizer import PlateRecognizer
from src.violation.violation_detector import ViolationDetector

logger = logging.getLogger(__name__)


class VideoProcessor:
    def __init__(self, camera_id: str, source_url: str, target_fps: int,
                 event_publisher: EventPublisher, settings: Settings):
        self.camera_id = camera_id
        self.source_url = source_url
        self.target_fps = target_fps
        self.event_publisher = event_publisher
        self.settings = settings
        self.running = False
        self.current_fps = 0
        self.vehicles_detected = 0
        self.started_at: Optional[datetime] = None
        self.frame_count = 0
        self.dropped_frames = 0

        self.vehicle_detector = VehicleDetector(settings)
        self.tracker = VehicleTracker()
        self.speed_estimator = SpeedEstimator()
        self.plate_recognizer = PlateRecognizer()
        self.violation_detector = ViolationDetector()

    async def run(self):
        self.running = True
        self.started_at = datetime.utcnow()
        cap = cv2.VideoCapture(self.source_url)
        if not cap.isOpened():
            logger.error(f"Failed to open video source: {self.source_url}")
            await self._publish_health_event("OFFLINE", "Failed to open video source")
            self.running = False
            return

        cap.set(cv2.CAP_PROP_BUFFERSIZE, 2)
        frame_interval = 1.0 / self.target_fps
        last_frame_time = 0

        while self.running:
            loop_start = time.time()
            ret, frame = cap.read()
            if not ret:
                logger.warning(f"Failed to read frame from {self.camera_id}")
                self.dropped_frames += 1
                await asyncio.sleep(0.01)
                continue

            self.frame_count += 1
            now = time.time()

            if now - last_frame_time < frame_interval:
                continue

            last_frame_time = now
            frame_rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)

            try:
                detections = self.vehicle_detector.detect(frame_rgb)
                tracks = self.tracker.update(detections, frame_rgb)
                speed_results = self.speed_estimator.estimate(
                    tracks, frame_interval
                ) if self.settings.SPEED_ESTIMATION_ENABLED else []
                plate_results = self.plate_recognizer.recognize(
                    frame_rgb, tracks
                ) if self.settings.PLATE_RECOGNITION_ENABLED else []
                violations = self.violation_detector.detect(
                    tracks, speed_results, plate_results
                ) if self.settings.VIOLATION_DETECTION_ENABLED else []

                await self._publish_detections(detections, tracks)
                await self._publish_plates(plate_results)
                await self._publish_speeds(speed_results)
                await self._publish_violations(violations)

                self.vehicles_detected = len(tracks)

            except Exception as e:
                logger.error(f"Processing error for {self.camera_id}: {e}", exc_info=True)
                self.dropped_frames += 1

            elapsed = time.time() - loop_start
            sleep_time = max(0, frame_interval - elapsed)
            if sleep_time > 0:
                await asyncio.sleep(sleep_time)

            self.current_fps = 1.0 / max(elapsed, 0.001)

        cap.release()
        logger.info(f"Video processor stopped for camera {self.camera_id}")

    async def stop(self):
        self.running = False
        logger.info(f"Stopping video processor for camera {self.camera_id}")

    async def _publish_health_event(self, status: str, message: str):
        await self.event_publisher.publish(
            topic=self.settings.KAFKA_CAMERA_HEALTH_TOPIC,
            payload={
                "status": status,
                "message": message,
                "processed_frames": self.frame_count,
                "dropped_frames": self.dropped_frames,
                "current_fps": self.current_fps,
            },
            camera_id=self.camera_id,
        )

    async def _publish_detections(self, detections, tracks):
        for track in tracks:
            if not track.detections:
                continue
            det = track.detections[-1]
            await self.event_publisher.publish(
                topic=self.settings.KAFKA_VEHICLE_OBSERVATIONS_TOPIC,
                payload={
                    "track_id": track.track_id,
                    "category": track.category.value if track.category else None,
                    "original_class_name": det.class_name,
                    "original_class_id": det.class_id,
                    "confidence": det.confidence,
                    "direction": track.direction,
                    "bbox": det.bbox,
                    "model_name": "yolov8",
                    "model_version": "1.0",
                },
                camera_id=self.camera_id,
                correlation_id=f"track-{track.track_id}",
            )

    async def _publish_plates(self, plate_results):
        for plate in plate_results:
            await self.event_publisher.publish(
                topic=self.settings.KAFKA_PLATE_RECOGNITIONS_TOPIC,
                payload={
                    "plate_text": plate.plate_text,
                    "normalized_plate": plate.normalized_plate,
                    "country": plate.country,
                    "country_confidence": plate.country_confidence,
                    "ocr_confidence": plate.ocr_confidence,
                    "quality_score": plate.quality_score,
                    "bbox": plate.bbox,
                },
                camera_id=self.camera_id,
            )

    async def _publish_speeds(self, speed_results):
        for speed in speed_results:
            await self.event_publisher.publish(
                topic=self.settings.KAFKA_SPEED_ESTIMATIONS_TOPIC,
                payload={
                    "track_id": speed.track_id,
                    "speed_kmh": speed.speed_kmh,
                    "smoothed_speed_kmh": speed.smoothed_speed_kmh,
                    "confidence": speed.confidence,
                    "distance_m": speed.distance_m,
                    "duration_ms": speed.duration_ms,
                    "algorithm_version": speed.algorithm_version,
                },
                camera_id=self.camera_id,
                correlation_id=f"track-{speed.track_id}",
            )

    async def _publish_violations(self, violations):
        for violation in violations:
            await self.event_publisher.publish(
                topic=self.settings.KAFKA_VIOLATION_CANDIDATES_TOPIC,
                payload=violation.model_dump(),
                camera_id=self.camera_id,
                correlation_id=f"violation-{violation.violation_type.value}-{datetime.utcnow().timestamp()}",
            )
