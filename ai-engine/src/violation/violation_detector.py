import logging
import time
from datetime import datetime, timezone
from typing import Dict, List, Optional, Tuple

from src.common.models import (
    Track, SpeedMeasurement, PlateResult, ViolationCandidate, ViolationType,
)

logger = logging.getLogger(__name__)

MIN_TRACK_HISTORY_POINTS = 10
MIN_TRACK_DISPLACEMENT_PX = 30


class CameraViolationConfig:
    def __init__(self, speed_limit_kmh: float = 60.0,
                 allowed_direction: Optional[str] = None,
                 overspeeding_enabled: bool = False,
                 wrong_way_enabled: bool = False):
        self.speed_limit_kmh = speed_limit_kmh
        self.allowed_direction = allowed_direction
        self.overspeeding_enabled = overspeeding_enabled
        self.wrong_way_enabled = wrong_way_enabled


class ViolationDetector:
    def __init__(self):
        self.camera_configs: Dict[str, CameraViolationConfig] = {}
        self._reported_violations: Dict[str, float] = {}
        self._dedup_ttl = 60.0

    def configure_camera(self, camera_id: str, config: CameraViolationConfig):
        self.camera_configs[camera_id] = config
        logger.info(f"Configured camera {camera_id}: speed_limit={config.speed_limit_kmh}, "
                     f"direction={config.allowed_direction}")

    def get_config(self, camera_id: str) -> CameraViolationConfig:
        return self.camera_configs.get(camera_id, CameraViolationConfig())

    def detect(self, tracks: List[Track],
               speed_measurements: List[SpeedMeasurement],
               plate_results: List[PlateResult],
               camera_id: str = "unknown") -> List[ViolationCandidate]:
        config = self.get_config(camera_id)
        violations: List[ViolationCandidate] = []

        if config.overspeeding_enabled:
            violations.extend(self._detect_overspeeding(speed_measurements, camera_id, config))

        if config.wrong_way_enabled and config.allowed_direction:
            violations.extend(self._detect_wrong_way(tracks, camera_id, config))

        return violations

    def _is_duplicate(self, dedup_key: str) -> bool:
        now = time.time()
        if dedup_key in self._reported_violations:
            age = now - self._reported_violations[dedup_key]
            if age < self._dedup_ttl:
                return True
        self._reported_violations[dedup_key] = now
        if len(self._reported_violations) > 1000:
            self._reported_violations = {
                k: v for k, v in self._reported_violations.items()
                if now - v < self._dedup_ttl
            }
        return False

    def _detect_overspeeding(self, speed_measurements: List[SpeedMeasurement],
                             camera_id: str, config: CameraViolationConfig) -> List[ViolationCandidate]:
        violations = []
        for sm in speed_measurements:
            if sm.speed_kmh > config.speed_limit_kmh * 1.1:
                dedup_key = f"{camera_id}:{sm.track_id}:OVERSPEEDING"
                if self._is_duplicate(dedup_key):
                    continue
                violations.append(ViolationCandidate(
                    camera_id=camera_id,
                    timestamp=datetime.now(timezone.utc),
                    violation_type=ViolationType.OVERSPEEDING,
                    confidence=min(1.0, sm.confidence),
                    track_id=sm.track_id,
                    speed_kmh=sm.speed_kmh,
                    speed_limit=config.speed_limit_kmh,
                ))
        return violations

    def _detect_wrong_way(self, tracks: List[Track],
                          camera_id: str, config: CameraViolationConfig) -> List[ViolationCandidate]:
        violations = []

        for track in tracks:
            if track.lost or track.completed:
                continue
            if not track.trajectory or len(track.trajectory) < MIN_TRACK_HISTORY_POINTS:
                continue

            traj = track.trajectory
            dx = traj[-1][0] - traj[0][0]
            dy = traj[-1][1] - traj[0][1]
            displacement = (dx ** 2 + dy ** 2) ** 0.5

            if displacement < MIN_TRACK_DISPLACEMENT_PX:
                continue

            recent = traj[-5:]
            recent_dx = recent[-1][0] - recent[0][0]
            recent_dy = recent[-1][1] - recent[0][1]

            if abs(recent_dx) > abs(recent_dy):
                actual_direction = "right-to-left" if recent_dx < 0 else "left-to-right"
            else:
                actual_direction = "bottom-to-top" if recent_dy < 0 else "top-to-bottom"

            if actual_direction == config.allowed_direction:
                continue

            dedup_key = f"{camera_id}:{track.track_id}:WRONG_WAY"
            if self._is_duplicate(dedup_key):
                continue

            logger.info(f"Wrong-way detection: track={track.track_id} "
                        f"actual={actual_direction} expected={config.allowed_direction} "
                        f"displacement={displacement:.1f}px camera={camera_id}")

            violations.append(ViolationCandidate(
                camera_id=camera_id,
                timestamp=datetime.now(timezone.utc),
                violation_type=ViolationType.WRONG_WAY_DRIVING,
                confidence=min(1.0, displacement / 200.0),
                track_id=track.track_id,
            ))

        return violations
