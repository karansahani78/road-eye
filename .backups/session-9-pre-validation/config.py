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
    KAFKA_INCIDENT_EVENTS_TOPIC: str = "incident-events"
    MODEL_PATH: str = "models/"
    MODEL_NAME: str = "yolov8n.pt"
    LOG_LEVEL: str = "INFO"

    # Detection
    # YOLOv8n (nano) produces lower-confidence predictions, especially for
    # small/distant vehicles. 0.3 recovers valid detections that 0.5 rejects.
    # The tracker's min_hits=3 + NMS (iou=0.5) already filter noise.
    DETECTION_CONFIDENCE_THRESHOLD: float = 0.3
    DETECTION_IOU_THRESHOLD: float = 0.5
    DETECTION_IMAGE_SIZE: int = 640
    DETECTION_MAX_DETECTIONS: int = 100
    DETECTION_DEVICE: str = ""
    # Run YOLO detection every N frames (1 = every frame, 2 = every other)
    DETECTION_FRAME_SKIP: int = 2

    # Plate
    PLATE_DETECTION_CONFIDENCE: float = 0.3
    PLATE_MODEL_PATH: str = "models/plate.pt"
    # Run OCR on a given track at most once per N frames
    OCR_TRACK_INTERVAL: int = 10

    # Helmet
    HELMET_DETECTION_CONFIDENCE: float = 0.3
    HELMET_MODEL_PATH: str = "models/helmet.pt"
    # Temporal confirmation: N consecutive frames with no-helmet before issuing violation
    HELMET_TEMPORAL_WINDOW: int = 5

    # Tracking
    TRACK_MAX_LOST: float = 5.0
    TRACK_MATCH_THRESHOLD: float = 0.3
    TRACK_MIN_HITS: int = 3

    # Feature toggles
    SPEED_ESTIMATION_ENABLED: bool = True
    PLATE_RECOGNITION_ENABLED: bool = True
    VIOLATION_DETECTION_ENABLED: bool = True
    HELMET_DETECTION_ENABLED: bool = True

    # Processing
    MAX_PROCESSING_FPS: int = 30
    # 0 = keep original camera resolution; set to e.g. 1280x720 to cap
    FRAME_WIDTH: int = 0
    FRAME_HEIGHT: int = 0

    # JPEG quality settings
    MJPEG_QUALITY: int = 75
    FRAME_QUALITY: int = 85
    EVIDENCE_QUALITY: int = 90

    # Diagnostic mode (development only — shows overlay metrics)
    ENABLE_DIAGNOSTIC_MODE: bool = False

    # Security
    API_KEY: str = ""
    """If non-empty, all endpoints require X-API-Key header matching this value."""

    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"
