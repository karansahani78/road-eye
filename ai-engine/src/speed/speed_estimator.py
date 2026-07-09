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
    def __init__(self, smoothing_factor: float = 0.3):
        self.pixels_per_meter: Optional[float] = None
        self.algorithm_version = "roadeye-speed-v1"
        self.calibration_version: Optional[str] = None
        self._prev_speeds: dict[int, float] = {}
        self.smoothing_factor = smoothing_factor

    def calibrate(self, reference_distance_m: float, reference_pixels: float,
                  calibration_version: Optional[str] = None):
        if reference_distance_m <= 0 or reference_pixels <= 0:
            logger.warning(f"Invalid calibration values: distance={reference_distance_m}, pixels={reference_pixels}")
            return
        self.pixels_per_meter = reference_pixels / reference_distance_m
        self.calibration_version = calibration_version
        logger.info(f"Speed calibrated: {self.pixels_per_meter:.4f} px/m "
                     f"(distance={reference_distance_m}m, pixels={reference_pixels}px)")

    def has_calibration(self) -> bool:
        return self.pixels_per_meter is not None and self.pixels_per_meter > 0

    def diagnostic_status(self) -> str:
        if not self.has_calibration():
            return "SPEED_UNAVAILABLE_NO_CALIBRATION"
        return "SPEED_AVAILABLE"

    def diagnostic_track_status(self, track: Track) -> str:
        if not self.has_calibration():
            return "SPEED_UNAVAILABLE_NO_CALIBRATION"
        if track.lost or track.completed:
            return "SPEED_UNAVAILABLE_TRACK_LOST"
        if len(track.trajectory) < MIN_TRAJECTORY_POINTS:
            return f"SPEED_UNAVAILABLE_INSUFFICIENT_HISTORY ({len(track.trajectory)}/<{MIN_TRAJECTORY_POINTS})"
        return "SPEED_AVAILABLE"

    def cleanup(self, active_track_ids: set):
        stale = [tid for tid in self._prev_speeds if tid not in active_track_ids]
        for tid in stale:
            del self._prev_speeds[tid]
        if stale:
            logger.debug(f"Cleaned {len(stale)} stale speed entries")

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

        # Use actual timestamps if available (trajectory point = [cx, cy, timestamp])
        # Fall back to frame_interval heuristic if timestamps are identical or missing
        pt = recent[0]
        last_pt = recent[-1]
        has_timestamps = len(pt) >= 3 and len(last_pt) >= 3 and last_pt[2] > pt[2]
        if has_timestamps:
            duration_s = last_pt[2] - pt[2]
        else:
            duration_s = len(recent) * frame_interval

        if duration_s <= 0:
            return None

        total_pixels = 0.0
        for i in range(1, len(recent)):
            dx = recent[i][0] - recent[i - 1][0]
            dy = recent[i][1] - recent[i - 1][1]
            total_pixels += (dx ** 2 + dy ** 2) ** 0.5

        distance_m = total_pixels / ppm if ppm > 0 else 0.0

        if distance_m <= 0:
            return None

        speed_mps = distance_m / duration_s
        speed_kmh = speed_mps * 3.6

        confidence = min(1.0, len(recent) / MAX_TRAJECTORY_POINTS)

        now = datetime.now(timezone.utc)
        smoothed = speed_kmh
        tid = track.track_id
        if tid in self._prev_speeds:
            smoothed = self.smoothing_factor * speed_kmh + (1 - self.smoothing_factor) * self._prev_speeds[tid]
        self._prev_speeds[tid] = smoothed
        return SpeedMeasurement(
            track_id=tid,
            speed_kmh=round(speed_kmh, 2),
            smoothed_speed_kmh=round(smoothed, 2),
            confidence=round(confidence, 4),
            distance_m=round(distance_m, 4),
            duration_ms=round(duration_s * 1000, 2),
            measured_at=now,
            calibration_version=self.calibration_version,
            algorithm_version=self.algorithm_version,
        )
