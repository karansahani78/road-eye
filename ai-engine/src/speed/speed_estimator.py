import logging
import time
from typing import List, Optional
from datetime import datetime, timezone

from src.common.models import SpeedMeasurement, Track

logger = logging.getLogger(__name__)

# Speed estimation requires calibration. Uncalibrated speed is never valid.
DEFAULT_PIXELS_PER_METER = None

# Minimum trajectory points for a speed estimate
MIN_TRAJECTORY_POINTS = 5

# Maximum trajectory points to use for speed calculation
MAX_TRAJECTORY_POINTS = 15


class SpeedEstimator:
    def __init__(self):
        self.pixels_per_meter: Optional[float] = None
        self.algorithm_version = "roadeye-speed-v1"

    def calibrate(self, reference_distance_m: float, reference_pixels: float):
        if reference_distance_m <= 0 or reference_pixels <= 0:
            logger.warning(f"Invalid calibration values: distance={reference_distance_m}, pixels={reference_pixels}")
            return
        self.pixels_per_meter = reference_pixels / reference_distance_m
        logger.info(f"Speed calibrated: {self.pixels_per_meter:.4f} px/m "
                     f"(distance={reference_distance_m}m, pixels={reference_pixels}px)")

    def has_calibration(self) -> bool:
        return self.pixels_per_meter is not None and self.pixels_per_meter > 0

    def estimate(self, tracks: List[Track], frame_interval: float) -> List[SpeedMeasurement]:
        if not self.has_calibration():
            return []

        results: List[SpeedMeasurement] = []
        ppm = self.pixels_per_meter

        for track in tracks:
            if track.lost or track.completed:
                continue

            speed = self._calculate_speed(track, frame_interval, ppm)
            if speed is not None:
                results.append(speed)

        return results

    def _calculate_speed(self, track: Track, frame_interval: float,
                         ppm: float) -> Optional[SpeedMeasurement]:
        traj = track.trajectory
        if len(traj) < MIN_TRAJECTORY_POINTS:
            return None

        recent = traj[-MAX_TRAJECTORY_POINTS:]
        if len(recent) < 2:
            return None

        total_pixels = 0.0
        for i in range(1, len(recent)):
            dx = recent[i][0] - recent[i - 1][0]
            dy = recent[i][1] - recent[i - 1][1]
            total_pixels += (dx ** 2 + dy ** 2) ** 0.5

        distance_m = total_pixels / ppm if ppm > 0 else 0.0
        duration_s = len(recent) * frame_interval

        if duration_s <= 0 or distance_m <= 0:
            return None

        speed_mps = distance_m / duration_s
        speed_kmh = speed_mps * 3.6

        confidence = min(1.0, len(recent) / MAX_TRAJECTORY_POINTS)

        now = datetime.now(timezone.utc)
        return SpeedMeasurement(
            track_id=track.track_id,
            speed_kmh=round(speed_kmh, 2),
            smoothed_speed_kmh=round(speed_kmh, 2),
            confidence=round(confidence, 4),
            distance_m=round(distance_m, 4),
            duration_ms=round(duration_s * 1000, 2),
            measured_at=now,
            algorithm_version=self.algorithm_version,
        )
