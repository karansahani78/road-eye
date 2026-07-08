import asyncio
import logging
from datetime import datetime, timezone
from enum import Enum
from typing import Dict, Optional

import cv2
import numpy as np

from src.config import Settings
from src.common.event_publisher import EventPublisher
from src.stream.video_processor import VideoProcessor

logger = logging.getLogger(__name__)


class StreamStartResult(Enum):
    SUCCESS = "success"
    STREAM_ALREADY_EXISTS = "stream_already_exists"
    CONNECTION_FAILED = "connection_failed"
    ERROR = "error"


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
            logger.error(f"Failed to open video source for camera {camera_id}: {source_url}")
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

        logger.info(f"Stream started for camera {camera_id}")
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

    async def get_frame(self, camera_id: str) -> Optional[np.ndarray]:
        processor = self.active_streams.get(camera_id)
        if processor is None:
            return None
        return await processor.get_latest_frame()
