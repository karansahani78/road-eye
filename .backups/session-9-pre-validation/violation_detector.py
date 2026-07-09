import logging
import time
from collections import defaultdict
from datetime import datetime, timezone
from typing import Dict, List, Optional, Tuple

from src.common.models import (
    Track, SpeedMeasurement, PlateResult, ViolationCandidate, ViolationType,
    HelmetResult,
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
    def __init__(self, detector_version: str = "1.0",
                 helmet_temporal_window: int = 5):
        self.detector_version = detector_version
        self.helmet_temporal_window = helmet_temporal_window
        self.camera_configs: Dict[str, CameraViolationConfig] = {}
        self._reported_violations: Dict[str, float] = {}
        self._dedup_ttl = 60.0
        # Track no-helmet state across frames: {track_id: [has_helmet, ...]}
        self._helmet_history: Dict[int, List[bool]] = defaultdict(list)
        # Track whether we already reported NO_HELMET for each track
        self._helmet_reported: set[int] = set()

    def configure_camera(self, camera_id: str, config: CameraViolationConfig):
        self.camera_configs[camera_id] = config
        logger.info(f"Configured camera {camera_id}: speed_limit={config.speed_limit_kmh}, "
                     f"direction={config.allowed_direction}")

    def get_config(self, camera_id: str) -> CameraViolationConfig:
        return self.camera_configs.get(camera_id, CameraViolationConfig())

    def detect(self, tracks: List[Track],
               speed_measurements: List[SpeedMeasurement],
               plate_results: List[PlateResult],
               camera_id: str = "unknown",
               helmet_results: Optional[Dict[int, List[HelmetResult]]] = None) -> List[ViolationCandidate]:
        config = self.get_config(camera_id)
        violations: List[ViolationCandidate] = []

        plate_by_track: Dict[int, PlateResult] = {}
        for pr in plate_results:
            if pr.track_id is not None:
                plate_by_track[pr.track_id] = pr

        track_by_id: Dict[int, Track] = {}
        for t in tracks:
            track_by_id[t.track_id] = t

        if config.overspeeding_enabled:
            violations.extend(self._detect_overspeeding(
                speed_measurements, camera_id, config, plate_by_track, track_by_id))

        if config.wrong_way_enabled and config.allowed_direction:
            violations.extend(self._detect_wrong_way(
                tracks, camera_id, config, plate_by_track, track_by_id))

        # Helmet detection: always check if we have helmet results
        if helmet_results:
            violations.extend(self._detect_no_helmet(
                helmet_results, camera_id, plate_by_track, track_by_id))

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

    def _detect_no_helmet(self, helmet_results: Dict[int, List[HelmetResult]],
                          camera_id: str,
                          plate_by_track: Dict[int, PlateResult],
                          track_by_id: Dict[int, Track]) -> List[ViolationCandidate]:
        violations = []
        now = time.time()

        for track_id, results in helmet_results.items():
            # Determine if this track has any Without Helmet detection
            any_without = any(not r.has_helmet for r in results)
            any_with = any(r.has_helmet for r in results)

            # Update temporal history
            self._helmet_history[track_id].append(not any_without)
            if len(self._helmet_history[track_id]) > self.helmet_temporal_window:
                self._helmet_history[track_id].pop(0)

            # Temporal confirmation: check if last N frames show no helmet
            history = self._helmet_history[track_id]
            if len(history) < self.helmet_temporal_window:
                continue

            no_helmet_count = sum(1 for h in history if not h)
            # Confirm no-helmet if >60% of window shows without helmet
            if no_helmet_count < max(2, self.helmet_temporal_window * 0.6):
                continue

            # Skip if we already reported for this track
            if track_id in self._helmet_reported:
                continue

            dedup_key = f"{camera_id}:{track_id}:NO_HELMET"
            if self._is_duplicate(dedup_key):
                continue

            self._helmet_reported.add(track_id)
            pr = plate_by_track.get(track_id)
            tr = track_by_id.get(track_id)

            confidence = min(1.0, no_helmet_count / self.helmet_temporal_window)
            violations.append(ViolationCandidate(
                camera_id=camera_id,
                timestamp=datetime.now(timezone.utc),
                violation_type=ViolationType.NO_HELMET,
                confidence=confidence,
                track_id=track_id,
                vehicle_category=(tr.category if tr else None),
                plate_text=(pr.plate_text if pr else None),
                normalized_plate=(pr.normalized_plate if pr else None),
                plate_country=(pr.country if pr else None),
                detector_version=self.detector_version,
            ))

        return violations

    def _detect_overspeeding(self, speed_measurements: List[SpeedMeasurement],
                             camera_id: str, config: CameraViolationConfig,
                             plate_by_track: Dict[int, PlateResult],
                             track_by_id: Dict[int, Track]) -> List[ViolationCandidate]:
        violations = []
        for sm in speed_measurements:
            if sm.speed_kmh > config.speed_limit_kmh * 1.1:
                dedup_key = f"{camera_id}:{sm.track_id}:OVERSPEEDING"
                if self._is_duplicate(dedup_key):
                    continue
                pr = plate_by_track.get(sm.track_id) if sm.track_id is not None else None
                tr = track_by_id.get(sm.track_id) if sm.track_id is not None else None
                violations.append(ViolationCandidate(
                    camera_id=camera_id,
                    timestamp=datetime.now(timezone.utc),
                    violation_type=ViolationType.OVERSPEEDING,
                    confidence=min(1.0, sm.confidence),
                    track_id=sm.track_id,
                    speed_kmh=sm.speed_kmh,
                    speed_limit=config.speed_limit_kmh,
                    vehicle_category=(tr.category if tr else None),
                    plate_text=(pr.plate_text if pr else None),
                    normalized_plate=(pr.normalized_plate if pr else None),
                    plate_country=(pr.country if pr else None),
                    detector_version=self.detector_version,
                ))
        return violations

    def _detect_wrong_way(self, tracks: List[Track],
                          camera_id: str, config: CameraViolationConfig,
                          plate_by_track: Dict[int, PlateResult],
                          track_by_id: Dict[int, Track]) -> List[ViolationCandidate]:
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

            pr = plate_by_track.get(track.track_id)
            tr = track_by_id.get(track.track_id)

            violations.append(ViolationCandidate(
                camera_id=camera_id,
                timestamp=datetime.now(timezone.utc),
                violation_type=ViolationType.WRONG_WAY_DRIVING,
                confidence=min(1.0, displacement / 200.0),
                track_id=track.track_id,
                vehicle_category=(tr.category if tr else None),
                plate_text=(pr.plate_text if pr else None),
                normalized_plate=(pr.normalized_plate if pr else None),
                plate_country=(pr.country if pr else None),
                detector_version=self.detector_version,
            ))

        return violations

    def cleanup_helmet_state(self, active_track_ids: set):
        stale = [tid for tid in self._helmet_history if tid not in active_track_ids]
        for tid in stale:
            del self._helmet_history[tid]
            self._helmet_reported.discard(tid)
