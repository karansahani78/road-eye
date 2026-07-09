from enum import Enum
from typing import Optional, List, Any
from pydantic import BaseModel
from datetime import datetime


class VehicleCategory(str, Enum):
    BICYCLE = "BICYCLE"
    CYCLE_RICKSHAW = "CYCLE_RICKSHAW"
    MOTORCYCLE = "MOTORCYCLE"
    SCOOTER = "SCOOTER"
    MOPED = "MOPED"
    THREE_WHEELER = "THREE_WHEELER"
    AUTO_RICKSHAW = "AUTO_RICKSHAW"
    TEMPO = "TEMPO"
    CAR = "CAR"
    TAXI = "TAXI"
    JEEP = "JEEP"
    SUV = "SUV"
    PICKUP_TRUCK = "PICKUP_TRUCK"
    VAN = "VAN"
    MINIVAN = "MINIVAN"
    MICROBUS = "MICROBUS"
    MINIBUS = "MINIBUS"
    BUS = "BUS"
    SCHOOL_BUS = "SCHOOL_BUS"
    PUBLIC_BUS = "PUBLIC_BUS"
    TRUCK = "TRUCK"
    LIGHT_TRUCK = "LIGHT_TRUCK"
    HEAVY_TRUCK = "HEAVY_TRUCK"
    TANKER = "TANKER"
    TRACTOR = "TRACTOR"
    TRAILER = "TRAILER"
    CONSTRUCTION_VEHICLE = "CONSTRUCTION_VEHICLE"
    AMBULANCE = "AMBULANCE"
    FIRE_ENGINE = "FIRE_ENGINE"
    POLICE_VEHICLE = "POLICE_VEHICLE"
    EMERGENCY_VEHICLE = "EMERGENCY_VEHICLE"
    GOVERNMENT_VEHICLE = "GOVERNMENT_VEHICLE"
    MILITARY_VEHICLE = "MILITARY_VEHICLE"
    SPECIAL_PURPOSE_VEHICLE = "SPECIAL_PURPOSE_VEHICLE"
    OTHER = "OTHER"
    UNKNOWN = "UNKNOWN"


class Detection(BaseModel):
    model_config = {'protected_namespaces': ()}
    bbox: List[float]
    confidence: float
    class_id: int
    class_name: str
    mapped_category: Optional[VehicleCategory] = None
    timestamp: Optional[datetime] = None


class Track(BaseModel):
    track_id: int
    detections: List[Detection]
    trajectory: List[List[float]]
    first_seen: Optional[float] = None
    last_seen: Optional[float] = None
    category: Optional[VehicleCategory] = None
    direction: Optional[str] = None
    confidence: float = 0.0
    lost: bool = False
    completed: bool = False
    confirmed: bool = False
    hit_count: int = 0


class PlateDetection(BaseModel):
    bbox: List[float]
    confidence: float


class PlateResult(BaseModel):
    plate_text: str
    normalized_plate: str
    country: str
    country_confidence: float
    ocr_confidence: float
    track_id: Optional[int] = None
    bbox: Optional[List[float]] = None
    quality_score: Optional[float] = None
    alternatives: Optional[List[str]] = None
    detector_version: Optional[str] = None
    ocr_model_version: Optional[str] = None
    plate_detector_version: Optional[str] = None


class HelmetResult(BaseModel):
    bbox: List[float]
    confidence: float
    has_helmet: bool
    class_id: int


class SpeedMeasurement(BaseModel):
    track_id: int
    speed_kmh: float
    smoothed_speed_kmh: float
    confidence: float
    distance_m: float
    duration_ms: float
    measured_at: Optional[datetime] = None
    calibration_version: Optional[str] = None
    algorithm_version: str = "roadeye-cv-v1"


class ViolationType(str, Enum):
    OVERSPEEDING = "OVERSPEEDING"
    RED_LIGHT_VIOLATION = "RED_LIGHT_VIOLATION"
    WRONG_WAY_DRIVING = "WRONG_WAY_DRIVING"
    ILLEGAL_PARKING = "ILLEGAL_PARKING"
    STOP_LINE_VIOLATION = "STOP_LINE_VIOLATION"
    RESTRICTED_ZONE_ENTRY = "RESTRICTED_ZONE_ENTRY"
    NO_HELMET = "NO_HELMET"
    TRIPLE_RIDING = "TRIPLE_RIDING"
    BLACKLISTED_VEHICLE = "BLACKLISTED_VEHICLE"
    STOLEN_VEHICLE = "STOLEN_VEHICLE"


class ViolationCandidate(BaseModel):
    camera_id: str
    timestamp: datetime
    violation_type: ViolationType
    confidence: float
    track_id: Optional[int] = None
    vehicle_category: Optional[VehicleCategory] = None
    plate_text: Optional[str] = None
    normalized_plate: Optional[str] = None
    plate_country: Optional[str] = None
    speed_kmh: Optional[float] = None
    speed_limit: Optional[float] = None
    detector_version: Optional[str] = None
    evidence_frames: Optional[List[str]] = None
    metadata: Optional[dict] = None


class VehicleObservation(BaseModel):
    camera_id: str
    timestamp: datetime
    track_id: int
    category: Optional[VehicleCategory] = None
    original_class_name: Optional[str] = None
    original_class_id: Optional[int] = None
    confidence: float
    model_name: str = "yolov8"
    model_version: str = "1.0"
    direction: Optional[str] = None
    bbox: Optional[List[float]] = None


class KafkaEvent(BaseModel):
    event_id: str
    correlation_id: str
    timestamp: datetime
    schema_version: str = "1.0"
    camera_id: str
    payload: Any
