import logging
from typing import List, Dict, Optional
import numpy as np
from datetime import datetime

from src.common.models import Detection, Track

logger = logging.getLogger(__name__)


class VehicleTracker:
    def __init__(self, max_lost: int = 30, iou_threshold: float = 0.3):
        self.tracks: Dict[int, Track] = {}
        self.next_track_id = 1
        self.max_lost = max_lost
        self.iou_threshold = iou_threshold

    def update(self, detections: List[Detection],
               frame: np.ndarray) -> List[Track]:
        now = datetime.utcnow()
        matched_track_ids = set()

        for det in detections:
            best_track_id = self._find_matching_track(det)
            if best_track_id is not None:
                track = self.tracks[best_track_id]
                track.detections.append(det)
                track.last_seen = now
                center = self._bbox_center(det.bbox)
                track.trajectory.append(center)
                track.lost = False
                if det.mapped_category and track.category is None:
                    track.category = det.mapped_category
                track.confidence = max(track.confidence, det.confidence)
                matched_track_ids.add(best_track_id)
            else:
                track = Track(
                    track_id=self.next_track_id,
                    detections=[det],
                    trajectory=[self._bbox_center(det.bbox)],
                    first_seen=now,
                    last_seen=now,
                    category=det.mapped_category,
                    confidence=det.confidence,
                )
                self.tracks[self.next_track_id] = track
                self.next_track_id += 1
                matched_track_ids.add(track.track_id)

        for track_id, track in list(self.tracks.items()):
            if track_id not in matched_track_ids:
                if track.lost:
                    if (now - track.last_seen).total_seconds() > self.max_lost:
                        track.completed = True
                else:
                    track.lost = True

            if len(track.trajectory) >= 2:
                track.direction = self._estimate_direction(track.trajectory)

        return [
            t for t in self.tracks.values()
            if not t.completed
        ]

    def _find_matching_track(self, det: Detection) -> Optional[int]:
        best_iou = self.iou_threshold
        best_track_id = None
        det_center = self._bbox_center(det.bbox)

        for track_id, track in self.tracks.items():
            if track.completed:
                continue
            if not track.detections:
                continue
            last_det = track.detections[-1]
            iou = self._compute_iou(det.bbox, last_det.bbox)
            if iou > best_iou:
                best_iou = iou
                best_track_id = track_id

        return best_track_id

    def _compute_iou(self, bbox1: List[float], bbox2: List[float]) -> float:
        x1 = max(bbox1[0], bbox2[0])
        y1 = max(bbox1[1], bbox2[1])
        x2 = min(bbox1[2], bbox2[2])
        y2 = min(bbox1[3], bbox2[3])
        if x2 < x1 or y2 < y1:
            return 0.0
        intersection = (x2 - x1) * (y2 - y1)
        area1 = (bbox1[2] - bbox1[0]) * (bbox1[3] - bbox1[1])
        area2 = (bbox2[2] - bbox2[0]) * (bbox2[3] - bbox2[1])
        union = area1 + area2 - intersection
        return intersection / union if union > 0 else 0.0

    def _bbox_center(self, bbox: List[float]) -> List[float]:
        return [(bbox[0] + bbox[2]) / 2, (bbox[1] + bbox[3]) / 2]

    def _estimate_direction(self, trajectory: List[List[float]]) -> str:
        if len(trajectory) < 2:
            return "unknown"
        dx = trajectory[-1][0] - trajectory[0][0]
        dy = trajectory[-1][1] - trajectory[0][1]
        if abs(dx) > abs(dy):
            return "left-to-right" if dx > 0 else "right-to-left"
        else:
            return "top-to-bottom" if dy > 0 else "bottom-to-top"
