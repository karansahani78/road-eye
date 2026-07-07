from pydantic_settings import BaseSettings
from typing import Optional


class Settings(BaseSettings):
    KAFKA_BOOTSTRAP_SERVERS: str = "localhost:9092"
    KAFKA_CAMERA_HEALTH_TOPIC: str = "camera-health"
    KAFKA_VEHICLE_OBSERVATIONS_TOPIC: str = "vehicle-observations"
    KAFKA_PLATE_RECOGNITIONS_TOPIC: str = "plate-recognitions"
    KAFKA_SPEED_ESTIMATIONS_TOPIC: str = "speed-estimations"
    KAFKA_VIOLATION_CANDIDATES_TOPIC: str = "violation-candidates"
    KAFKA_EVIDENCE_CREATED_TOPIC: str = "evidence-created"
    MODEL_PATH: str = "models/"
    LOG_LEVEL: str = "INFO"
    VEHICLE_DETECTION_CONFIDENCE: float = 0.5
    PLATE_DETECTION_CONFIDENCE: float = 0.3
    SPEED_ESTIMATION_ENABLED: bool = True
    PLATE_RECOGNITION_ENABLED: bool = True
    VIOLATION_DETECTION_ENABLED: bool = True
    MAX_PROCESSING_FPS: int = 30
    FRAME_WIDTH: int = 1280
    FRAME_HEIGHT: int = 720

    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"
