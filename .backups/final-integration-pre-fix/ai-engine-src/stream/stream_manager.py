import asyncio
import logging
import socket
from datetime import datetime, timezone
from enum import Enum
from typing import Dict, Optional
from urllib.parse import urlparse

import cv2
import numpy as np

from src.config import Settings
from src.common.event_publisher import EventPublisher
from src.stream.video_processor import VideoProcessor
from src.violation.violation_detector import CameraViolationConfig

logger = logging.getLogger(__name__)


class StreamStartResult(Enum):
    SUCCESS = "success"
    STREAM_ALREADY_EXISTS = "stream_already_exists"
    CONNECTION_FAILED = "connection_failed"
    CONNECTION_FAILED_AUTH = "connection_failed_auth"
    CONNECTION_FAILED_UNREACHABLE = "connection_failed_unreachable"
    ERROR = "error"


def _diagnose_rtsp_error(source_url: str) -> str:
    """Classify RTSP connection failure reason without exposing credentials."""
    try:
        parsed = urlparse(source_url)
        hostname = parsed.hostname
        if not hostname:
            return "INVALID_SOURCE_URL"

        port = parsed.port or 554

        # Test TCP connectivity
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.settimeout(5.0)
        try:
            result = sock.connect_ex((hostname, port))
        finally:
            sock.close()

        if result != 0:
            return "CONNECTION_REFUSED"

        # Socket connects — TCP is fine. Likely auth or stream path issue.
        if parsed.username and parsed.password:
            return "AUTHENTICATION_FAILED"

        return "STREAM_NOT_FOUND"
    except Exception:
        return "UNKNOWN_CONNECTION_ERROR"


def _sanitize_url(url: str) -> str:
    """Strip password from RTSP URL for safe logging."""
    if not url:
        return url
    try:
        at_idx = url.find('@')
        if at_idx > 0:
            scheme_end = url.find('://')
            if scheme_end >= 0:
                userinfo_end = scheme_end + 3
                colon_idx = url.find(':', userinfo_end)
                if 0 < colon_idx < at_idx:
                    safe = url[:colon_idx + 1] + '****' + url[at_idx:]
                    return safe
    except Exception:
        pass
    return url


class StreamManager:
    def __init__(self, settings: Settings):
        self.settings = settings
        self.event_publisher = EventPublisher(settings.KAFKA_BOOTSTRAP_SERVERS)
        self.active_streams: Dict[str, VideoProcessor] = {}
        self._tasks: Dict[str, asyncio.Task] = {}

    async def start(self):
        await self.event_publisher.start()
        logger.info("Stream manager started")

    async def stop(self):
        logger.info("Stopping all streams...")
        camera_ids = list(self.active_streams.keys())
        for cid in camera_ids:
            await self.stop_stream(cid)
        await self.event_publisher.stop()
        # Cancel remaining tasks
        for cid, task in self._tasks.items():
            if not task.done():
                task.cancel()
        self._tasks.clear()
        logger.info("Stream manager stopped")

    async def start_stream(self, camera_id: str, source_url: str, fps: int = 15) -> StreamStartResult:
        if camera_id in self.active_streams:
            logger.warning(f"Stream already active for camera {camera_id}")
            return StreamStartResult.STREAM_ALREADY_EXISTS

        # Validate source URL BEFORE creating the processor
        # OpenCV VideoCapture must open successfully
        test_cap = cv2.VideoCapture(source_url)
        if not test_cap.isOpened():
            test_cap.release()
            logger.error(f"Failed to open video source for camera {camera_id}: source_url={_sanitize_url(source_url)}")
            error_type = _diagnose_rtsp_error(source_url)
            if error_type == "AUTHENTICATION_FAILED":
                return StreamStartResult.CONNECTION_FAILED_AUTH
            elif error_type == "CONNECTION_REFUSED":
                return StreamStartResult.CONNECTION_FAILED_UNREACHABLE
            return StreamStartResult.CONNECTION_FAILED
        test_cap.release()

        processor = VideoProcessor(
            camera_id=camera_id,
            source_url=source_url,
            target_fps=fps,
            event_publisher=self.event_publisher,
            settings=self.settings,
        )
        self.active_streams[camera_id] = processor
        task = asyncio.create_task(processor.run())
        self._tasks[camera_id] = task

        logger.info(f"Stream started for camera {camera_id}: source_url={_sanitize_url(source_url)}")
        return StreamStartResult.SUCCESS

    async def stop_stream(self, camera_id: str) -> bool:
        if camera_id not in self.active_streams:
            logger.warning(f"No active stream for camera {camera_id}")
            return False

        processor = self.active_streams[camera_id]
        await processor.stop()

        # Wait briefly for clean shutdown
        task = self._tasks.get(camera_id)
        if task and not task.done():
            try:
                await asyncio.wait_for(task, timeout=3.0)
            except asyncio.TimeoutError:
                logger.warning(f"Stream {camera_id} did not stop gracefully, cancelling")
                task.cancel()
            except Exception as e:
                logger.error(f"Error stopping stream {camera_id}: {e}")

        del self.active_streams[camera_id]
        if camera_id in self._tasks:
            del self._tasks[camera_id]

        logger.info(f"Stream stopped for camera {camera_id}")
        return True

    def _resolve_status(self, processor: VideoProcessor) -> str:
        if processor.error:
            return "ERROR"
        if processor.running:
            return "RUNNING"
        return "STOPPED"

    def get_stream_info(self) -> Dict:
        info = {}
        for cid, processor in self.active_streams.items():
            info[cid] = {
                "status": self._resolve_status(processor),
                "fps": processor.current_fps,
                "vehicles_detected": processor.vehicles_detected,
                "frame_count": processor.frame_count,
                "dropped_frames": processor.dropped_frames,
                "started_at": processor.started_at.isoformat() if processor.started_at else None,
            }
        return info

    def get_stream_status(self, camera_id: str) -> Optional[Dict]:
        processor = self.active_streams.get(camera_id)
        if processor is None:
            return None
        return {
            "status": self._resolve_status(processor),
            "fps": processor.current_fps,
            "vehicles_detected": processor.vehicles_detected,
            "frame_count": processor.frame_count,
            "dropped_frames": processor.dropped_frames,
            "frame_ready": processor.frame_ready,
            "started_at": processor.started_at.isoformat() if processor.started_at else None,
            "error_message": processor.error_message if processor.error else None,
        }

    async def calibrate_stream(self, camera_id: str, known_distance_m: float,
                                reference_pixels: float,
                                calibration_data: Optional[str] = None) -> bool:
        processor = self.active_streams.get(camera_id)
        if processor is None:
            logger.warning(f"Cannot calibrate: no active stream for camera {camera_id}")
            return False
        processor.set_calibration(known_distance_m, reference_pixels, calibration_data)
        logger.info(f"Stream calibration applied for camera {camera_id}")
        return True

    def configure_violation_detection(self, camera_id: str, config: CameraViolationConfig):
        processor = self.active_streams.get(camera_id)
        if processor is None:
            logger.info(f"Pre-configuring violation detection for camera {camera_id} (will apply on stream start)")
            return
        processor.violation_detector.configure_camera(camera_id, config)
        logger.info(f"Violation detection configured for camera {camera_id}")

    async def get_frame(self, camera_id: str) -> Optional[np.ndarray]:
        processor = self.active_streams.get(camera_id)
        if processor is None:
            return None
        return await processor.get_latest_frame()
