import asyncio
import logging
from typing import Dict, Optional
from datetime import datetime

from src.config import Settings
from src.common.event_publisher import EventPublisher
from src.stream.video_processor import VideoProcessor

logger = logging.getLogger(__name__)


class StreamManager:
    def __init__(self, settings: Settings):
        self.settings = settings
        self.active_streams: Dict[str, VideoProcessor] = {}
        self.event_publisher = EventPublisher(settings.KAFKA_BOOTSTRAP_SERVERS)

    async def start(self):
        await self.event_publisher.start()
        logger.info("Stream manager started")

    async def stop(self):
        for camera_id, processor in list(self.active_streams.items()):
            await processor.stop()
        self.active_streams.clear()
        await self.event_publisher.stop()
        logger.info("Stream manager stopped")

    async def start_stream(self, camera_id: str, source_url: str,
                           fps: int = 15) -> bool:
        if camera_id in self.active_streams:
            logger.warning(f"Stream already active for camera {camera_id}")
            return False
        processor = VideoProcessor(
            camera_id=camera_id,
            source_url=source_url,
            target_fps=fps,
            event_publisher=self.event_publisher,
            settings=self.settings,
        )
        self.active_streams[camera_id] = processor
        asyncio.create_task(processor.run())
        logger.info(f"Started stream for camera {camera_id}")
        return True

    async def stop_stream(self, camera_id: str) -> bool:
        processor = self.active_streams.pop(camera_id, None)
        if processor:
            await processor.stop()
            logger.info(f"Stopped stream for camera {camera_id}")
            return True
        logger.warning(f"No active stream for camera {camera_id}")
        return False

    def get_stream_info(self) -> Dict:
        return {
            sid: {
                "status": "running" if p.running else "stopped",
                "fps": p.current_fps,
                "vehicles_detected": p.vehicles_detected,
                "started_at": p.started_at.isoformat() if p.started_at else None,
            }
            for sid, p in self.active_streams.items()
        }
