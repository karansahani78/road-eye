import json
import logging
import uuid
from datetime import datetime, timezone
from typing import Any, Dict, Optional

from aiokafka import AIOKafkaProducer

logger = logging.getLogger(__name__)


class EventPublisher:
    def __init__(self, bootstrap_servers: str):
        self.bootstrap_servers = bootstrap_servers
        self.producer: Optional[AIOKafkaProducer] = None
        self._started = False

    async def start(self):
        if self._started:
            return
        try:
            self.producer = AIOKafkaProducer(
                bootstrap_servers=self.bootstrap_servers,
                acks=1,
                compression_type="gzip",
                linger_ms=10,
            )
            await self.producer.start()
            self._started = True
            logger.info(f"Kafka producer started (bootstrap: {self.bootstrap_servers})")
        except Exception as e:
            logger.error(f"Failed to start Kafka producer: {e}")
            self.producer = None
            self._started = False

    async def stop(self):
        self._started = False
        if self.producer is not None:
            try:
                await self.producer.stop()
                logger.info("Kafka producer stopped")
            except Exception as e:
                logger.error(f"Error stopping Kafka producer: {e}")
            finally:
                self.producer = None

    async def publish(self, topic: str, payload: Dict[str, Any],
                      camera_id: str, correlation_id: Optional[str] = None) -> bool:
        if not self._started or self.producer is None:
            logger.warning(f"Kafka producer not started, cannot publish to {topic}")
            return False

        event = {
            "event_id": str(uuid.uuid4()),
            "correlation_id": correlation_id or str(uuid.uuid4()),
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "schema_version": "1.0",
            "camera_id": camera_id,
            "payload": payload,
        }

        try:
            value = json.dumps(event, default=str).encode("utf-8")
            key = camera_id.encode("utf-8")
            await self.producer.send(topic, value=value, key=key)
            logger.debug(f"Published to {topic}: camera={camera_id} correlation={correlation_id}")
            return True
        except Exception as e:
            logger.error(f"Failed to publish to {topic}: {e}")
            return False
