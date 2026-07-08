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
    MODEL_NAME: str = "yolov8n.pt"
    LOG_LEVEL: str = "INFO"

    # Detection
    DETECTION_CONFIDENCE_THRESHOLD: float = 0.5
    DETECTION_IOU_THRESHOLD: float = 0.5
    DETECTION_IMAGE_SIZE: int = 640
    DETECTION_MAX_DETECTIONS: int = 100
    DETECTION_DEVICE: str = ""

    # Plate
    PLATE_DETECTION_CONFIDENCE: float = 0.3

    # Tracking
    TRACK_MAX_LOST: float = 5.0
    TRACK_MATCH_THRESHOLD: float = 0.3
    TRACK_MIN_HITS: int = 3

    # Feature toggles
    SPEED_ESTIMATION_ENABLED: bool = True
    PLATE_RECOGNITION_ENABLED: bool = True
    VIOLATION_DETECTION_ENABLED: bool = True

    # Processing
    MAX_PROCESSING_FPS: int = 30
    FRAME_WIDTH: int = 1280
    FRAME_HEIGHT: int = 720

    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"
