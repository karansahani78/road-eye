import logging
from typing import List, Optional
import numpy as np

from src.common.models import SpeedMeasurement, Track

logger = logging.getLogger(__name__)


class SpeedEstimator:
    def __init__(self):
        self.pixels_per_meter: Optional[float] = None
        self.algorithm_version = "roadeye-cv-v1"

    def calibrate(self, reference_distance_m: float,
                  reference_pixels: float):
        self.pixels_per_meter = reference_pixels / reference_distance_m
        logger.info(f"Calibrated: {self.pixels_per_meter:.2f} pixels/meter")

    def estimate(self, tracks: List[Track],
                 frame_interval: float) -> List[SpeedMeasurement]:
        results = []
        for track in tracks:
            if len(track.trajectory) < 5:
                continue

            speed = self._calculate_speed(track, frame_interval)
            if speed is not None:
                results.append(speed)

        return results

    def _calculate_speed(self, track: Track,
                         frame_interval: float) -> Optional[SpeedMeasurement]:
        try:
            recent = track.trajectory[-5:]
            if len(recent) < 2:
                return None

            total_pixels = 0
            for i in range(1, len(recent)):
                dx = recent[i][0] - recent[i-1][0]
                dy = recent[i][1] - recent[i-1][1]
                total_pixels += np.sqrt(dx**2 + dy**2)

            if self.pixels_per_meter and self.pixels_per_meter > 0:
                distance_m = total_pixels / self.pixels_per_meter
            else:
                distance_m = total_pixels * 0.01  # default rough estimate

            duration_s = len(recent) * frame_interval
            speed_ms = distance_m / duration_s if duration_s > 0 else 0
            speed_kmh = speed_ms * 3.6

            confidence = min(1.0, len(recent) / 20.0)

            return SpeedMeasurement(
                track_id=track.track_id,
                speed_kmh=speed_kmh,
                smoothed_speed_kmh=speed_kmh,
                confidence=confidence,
                distance_m=distance_m,
                duration_ms=duration_s * 1000,
                algorithm_version=self.algorithm_version,
            )
        except Exception as e:
            logger.error(f"Speed calculation error for track {track.track_id}: {e}")
            return None
