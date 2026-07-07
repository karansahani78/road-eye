import logging
from typing import List, Optional
from datetime import datetime

from src.common.models import (
    ViolationCandidate, ViolationType, Track,
    SpeedMeasurement, PlateResult, VehicleCategory,
)

logger = logging.getLogger(__name__)


class ViolationDetector:
    def __init__(self):
        self.speed_limits: dict = {}

    def set_speed_limit(self, camera_id: str, limit_kmh: float):
        self.speed_limits[camera_id] = limit_kmh

    def detect(self, tracks: List[Track],
               speed_measurements: List[SpeedMeasurement],
               plate_results: List[PlateResult],
               camera_id: Optional[str] = None) -> List[ViolationCandidate]:
        violations = []

        violations.extend(self._detect_overspeeding(
            speed_measurements, camera_id))
        violations.extend(self._detect_wrong_way(tracks))
        violations.extend(self._detect_no_helmet(tracks, plate_results))

        return violations

    def _detect_overspeeding(
            self, speed_measurements: List[SpeedMeasurement],
            camera_id: Optional[str] = None) -> List[ViolationCandidate]:
        violations = []
        speed_limit = self.speed_limits.get(camera_id, 60) if camera_id else 60

        for speed in speed_measurements:
            if speed.speed_kmh > speed_limit * 1.1:
                violations.append(ViolationCandidate(
                    camera_id=camera_id or "unknown",
                    timestamp=datetime.utcnow(),
                    violation_type=ViolationType.OVERSPEEDING,
                    confidence=speed.confidence,
                    track_id=speed.track_id,
                    speed_kmh=speed.speed_kmh,
                    speed_limit=speed_limit,
                    metadata={
                        "smoothed_speed": speed.smoothed_speed_kmh,
                        "algorithm_version": speed.algorithm_version,
                    },
                ))

        return violations

    def _detect_wrong_way(self, tracks: List[Track]) -> List[ViolationCandidate]:
        violations = []
        expected_direction = "left-to-right"

        for track in tracks:
            if track.direction and track.direction not in (
                    "unknown", expected_direction):
                if track.confidence > 0.5:
                    violations.append(ViolationCandidate(
                        camera_id="unknown",
                        timestamp=datetime.utcnow(),
                        violation_type=ViolationType.WRONG_WAY_DRIVING,
                        confidence=track.confidence * 0.7,
                        track_id=track.track_id,
                        vehicle_category=track.category,
                        metadata={"direction": track.direction},
                    ))

        return violations

    def _detect_no_helmet(
            self, tracks: List[Track],
            plate_results: List[PlateResult]) -> List[ViolationCandidate]:
        violations = []
        for track in tracks:
            if track.category in (
                    VehicleCategory.MOTORCYCLE,
                    VehicleCategory.SCOOTER,
                    VehicleCategory.MOPED,
            ):
                violations.append(ViolationCandidate(
                    camera_id="unknown",
                    timestamp=datetime.utcnow(),
                    violation_type=ViolationType.NO_HELMET,
                    confidence=0.3,
                    track_id=track.track_id,
                    vehicle_category=track.category,
                    metadata={"note": "basic detection, requires dedicated helmet model"},
                ))
        return violations
