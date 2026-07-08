import logging
import time
from typing import Dict, List, Optional, Tuple
import numpy as np

from src.common.models import Detection, Track, VehicleCategory

logger = logging.getLogger(__name__)

TRAFFIC_CATEGORIES = frozenset({
    VehicleCategory.BICYCLE,
    VehicleCategory.MOTORCYCLE,
    VehicleCategory.CAR,
    VehicleCategory.BUS,
    VehicleCategory.TRUCK,
})


class KalmanFilter1D:
    def __init__(self, init_val: float, Q: float = 1.0, R: float = 0.1):
        self.x = init_val
        self.P = 1.0
        self.Q = Q
        self.R = R

    def predict(self, dt: float = 1.0):
        self.P += self.Q * dt
        return self.x

    def update(self, z: float):
        K = self.P / (self.P + self.R)
        self.x += K * (z - self.x)
        self.P = (1 - K) * self.P
        return self.x


class TrackState:
    def __init__(self, cx: float, cy: float, category: VehicleCategory, track_id: int):
        self.track_id = track_id
        self.category = category
        self.cx = cx
        self.cy = cy
        self.vx = 0.0
        self.vy = 0.0
        self.kf_x = KalmanFilter1D(cx)
        self.kf_y = KalmanFilter1D(cy)
        self.kf_w = KalmanFilter1D(0.0)
        self.kf_h = KalmanFilter1D(0.0)
        self.detections: List[Detection] = []
        self.trajectory: List[Tuple[float, float]] = [(cx, cy)]
        self.first_seen: float = time.time()
        self.last_seen: float = time.time()
        self.lost: bool = False
        self.completed: bool = False
        self.confirmed: bool = False
        self.hit_count: int = 1
        self.direction: str = "stationary"
        self.last_bbox: List[float] = [0, 0, 0, 0]

    def predict(self, dt: float = 1.0):
        px = self.kf_x.predict(dt)
        py = self.kf_y.predict(dt)
        self.cx = px + self.vx * dt
        self.cy = py + self.vy * dt
        return (self.cx, self.cy)

    def update(self, det: Detection, now: float):
        bbox = det.bbox
        cx = (bbox[0] + bbox[2]) / 2
        cy = (bbox[1] + bbox[3]) / 2
        w = bbox[2] - bbox[0]
        h = bbox[3] - bbox[1]

        dt = max(0.001, now - self.last_seen)

        # Update velocity
        self.vx = (cx - self.cx) / dt
        self.vy = (cy - self.cy) / dt

        # Clamp velocity
        max_v = 500.0
        self.vx = max(-max_v, min(max_v, self.vx))
        self.vy = max(-max_v, min(max_v, self.vy))

        self.cx = cx
        self.cy = cy
        self.kf_x.update(cx)
        self.kf_y.update(cy)
        self.kf_w.update(w)
        self.kf_h.update(h)
        self.last_bbox = bbox

        self.detections.append(det)
        self.trajectory.append((cx, cy))
        self.last_seen = now
        self.lost = False
        self.completed = False
        self.hit_count = len(self.detections)
        self.category = det.mapped_category

        if not self.confirmed and self.hit_count >= 3:
            self.confirmed = True
            logger.debug(f"Track {self.track_id} CONFIRMED after {self.hit_count} hits")

        self.direction = self._estimate_direction()

    def mark_lost(self, now: float):
        if not self.lost:
            self.lost = True
            self.last_seen = now

    def mark_completed(self):
        self.completed = True

    def expired(self, max_lost_seconds: float, now: float) -> bool:
        return self.lost and (now - self.last_seen) > max_lost_seconds

    def _estimate_direction(self) -> str:
        traj = self.trajectory
        if len(traj) < 2:
            return "stationary"
        recent = traj[-5:] if len(traj) >= 5 else traj
        dx = recent[-1][0] - recent[0][0]
        dy = recent[-1][1] - recent[0][1]

        if abs(dx) > abs(dy):
            return "right-to-left" if dx < 0 else "left-to-right"
        elif abs(dy) > 0:
            return "bottom-to-top" if dy < 0 else "top-to-bottom"
        else:
            return "stationary"


class VehicleTracker:
    def __init__(self, max_lost: float = 5.0, iou_threshold: float = 0.3,
                 min_hits: int = 3):
        self.max_lost_seconds = max_lost
        self.iou_threshold = iou_threshold
        self.min_hits = min_hits
        self._states: Dict[int, TrackState] = {}
        self._next_track_id = 1

    def _is_traffic_detection(self, det: Detection) -> bool:
        return det.mapped_category is not None and det.mapped_category in TRAFFIC_CATEGORIES

    def update(self, detections: List[Detection], frame=None) -> List[Track]:
        now = time.time()
        matched_ids = set()

        # Predict all track positions
        for state in self._states.values():
            if not state.lost and not state.completed:
                state.predict(0.033)

        for det in detections:
            if not self._is_traffic_detection(det):
                continue

            track_id = self._match_track(det)
            if track_id is not None:
                self._states[track_id].update(det, now)
                matched_ids.add(track_id)
            else:
                state = self._create_state(det, now)
                matched_ids.add(state.track_id)

        for tid, state in self._states.items():
            if tid not in matched_ids and not state.lost:
                state.mark_lost(now)

        expired_ids = [
            tid for tid, state in self._states.items()
            if state.expired(self.max_lost_seconds, now)
        ]
        for tid in expired_ids:
            self._states[tid].mark_completed()
            del self._states[tid]

        return self._to_track_list()

    def get_confirmed_tracks(self) -> List[Track]:
        return [
            self._to_track(state)
            for state in self._states.values()
            if state.confirmed and not state.lost and not state.completed
        ]

    def get_all_tracks(self) -> List[Track]:
        return self._to_track_list()

    def _create_state(self, det: Detection, now: float) -> TrackState:
        bbox = det.bbox
        cx = (bbox[0] + bbox[2]) / 2
        cy = (bbox[1] + bbox[3]) / 2
        tid = self._next_track_id
        self._next_track_id += 1
        state = TrackState(cx, cy, det.mapped_category, tid)
        state.update(det, now)
        self._states[tid] = state
        return state

    def _match_track(self, det: Detection) -> Optional[int]:
        best_id = None
        best_cost = self.iou_threshold

        for tid, state in self._states.items():
            if state.lost or state.completed:
                continue
            if not state.detections:
                continue

            cost = self._association_cost(det, state)
            if cost > best_cost:
                best_cost = cost
                best_id = tid

        return best_id

    def _association_cost(self, det: Detection, state: TrackState) -> float:
        iou = self._compute_iou(det.bbox, state.last_bbox)

        if len(state.trajectory) >= 2:
            det_cx = (det.bbox[0] + det.bbox[2]) / 2
            det_cy = (det.bbox[1] + det.bbox[3]) / 2

            # Distance from Kalman prediction
            pred_dx = det_cx - state.cx
            pred_dy = det_cy - state.cy
            pred_dist = (pred_dx ** 2 + pred_dy ** 2) ** 0.5
            pred_score = max(0, 1.0 - pred_dist / 200.0)

            combined = 0.6 * iou + 0.4 * pred_score
            return combined

        return iou

    def _compute_iou(self, bbox1: List[float], bbox2: List[float]) -> float:
        x1 = max(bbox1[0], bbox2[0])
        y1 = max(bbox1[1], bbox2[1])
        x2 = min(bbox1[2], bbox2[2])
        y2 = min(bbox1[3], bbox2[3])

        inter = max(0, x2 - x1) * max(0, y2 - y1)
        if inter <= 0:
            return 0.0

        area1 = (bbox1[2] - bbox1[0]) * (bbox1[3] - bbox1[1])
        area2 = (bbox2[2] - bbox2[0]) * (bbox2[3] - bbox2[1])
        union = area1 + area2 - inter

        return inter / union if union > 0 else 0.0

    def _to_track(self, state: TrackState) -> Track:
        dets = state.detections
        conf = dets[-1].confidence if dets else 0.0
        traj_list = [[p[0], p[1]] for p in state.trajectory]
        return Track(
            track_id=state.track_id,
            detections=dets,
            trajectory=traj_list,
            first_seen=state.first_seen,
            last_seen=state.last_seen,
            category=state.category,
            direction=state.direction,
            confidence=conf,
            lost=state.lost,
            completed=state.completed,
            confirmed=state.confirmed,
            hit_count=state.hit_count,
        )

    def _to_track_list(self) -> List[Track]:
        return [self._to_track(s) for s in self._states.values()]
