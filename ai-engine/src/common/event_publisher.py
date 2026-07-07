import json
import logging
import uuid
from datetime import datetime
from typing import Optional, Any
from aiokafka import AIOKafkaProducer

from src.common.models import KafkaEvent

logger = logging.getLogger(__name__)


class EventPublisher:
    def __init__(self, bootstrap_servers: str):
        self.bootstrap_servers = bootstrap_servers
        self.producer: Optional[AIOKafkaProducer] = None

    async def start(self):
        self.producer = AIOKafkaProducer(
            bootstrap_servers=self.bootstrap_servers,
            value_serializer=lambda v: json.dumps(v, default=str).encode(),
        )
        await self.producer.start()
        logger.info("Kafka producer started")

    async def stop(self):
        if self.producer:
            await self.producer.stop()
            logger.info("Kafka producer stopped")

    async def publish(self, topic: str, payload: Any, camera_id: str,
                      correlation_id: Optional[str] = None) -> bool:
        if not self.producer:
            logger.error("Producer not initialized")
            return False
        try:
            event = KafkaEvent(
                event_id=str(uuid.uuid4()),
                correlation_id=correlation_id or str(uuid.uuid4()),
                timestamp=datetime.utcnow(),
                camera_id=camera_id,
                payload=payload,
            )
            await self.producer.send(topic, value=event.model_dump())
            logger.debug(f"Published event to {topic}: {event.event_id}")
            return True
        except Exception as e:
            logger.error(f"Failed to publish event to {topic}: {e}")
            return False
