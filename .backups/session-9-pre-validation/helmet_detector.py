import logging
import os
from typing import List, Optional, Tuple

import numpy as np

from src.common.models import HelmetResult

logger = logging.getLogger(__name__)

# Class name constants from the trained model
WITH_HELMET = 0
WITHOUT_HELMET = 1


class HelmetDetector:
    def __init__(self, model_path: str, conf_threshold: float = 0.3):
        self.model_path = model_path
        self.conf_threshold = conf_threshold
        self.model: Optional = None
        self.model_version = "1.0"
        self._load_model()

    def _load_model(self):
        if not os.path.exists(self.model_path):
            logger.warning(f"Helmet detection model not found at {self.model_path}")
            return
        try:
            from ultralytics import YOLO
            self.model = YOLO(self.model_path)
            base = os.path.basename(self.model_path)
            name = os.path.splitext(base)[0]
            self.model_version = f"yolov8-{name}"
            class_names = self.model.names if hasattr(self.model, 'names') else {}
            logger.info(f"Loaded helmet detection model from {self.model_path}: "
                         f"classes={class_names}")
        except Exception as e:
            logger.error(f"Failed to load helmet detection model: {e}")
            self.model = None

    def detect(self, frame: np.ndarray, vehicle_bbox: List[float]) -> List[HelmetResult]:
        if self.model is None:
            return []

        h, w = frame.shape[:2]
        x1 = max(0, int(vehicle_bbox[0]))
        y1 = max(0, int(vehicle_bbox[1]))
        x2 = min(w, int(vehicle_bbox[2]))
        y2 = min(h, int(vehicle_bbox[3]))

        if x2 <= x1 or y2 <= y1:
            return []

        # Focus on the upper portion of the vehicle (where rider head is)
        # For motorcycles, the rider sits in the upper half of the bbox
        upper_roi_y1 = y1
        upper_roi_y2 = y1 + int((y2 - y1) * 0.55)
        roi = frame[upper_roi_y1:upper_roi_y2, x1:x2]

        if roi.size == 0:
            return []

        results = self.model(
            roi,
            verbose=False,
            conf=self.conf_threshold,
            iou=0.5,
            max_det=5,
        )

        detections = []
        for result in results:
            boxes = result.boxes
            if boxes is None:
                continue
            for i in range(len(boxes)):
                class_id = int(boxes.cls[i])
                conf = float(boxes.conf[i])
                bbox = boxes.xyxy[i].tolist()
                abs_bbox = [
                    bbox[0] + x1,
                    bbox[1] + upper_roi_y1,
                    bbox[2] + x1,
                    bbox[3] + upper_roi_y1,
                ]
                detections.append(HelmetResult(
                    bbox=abs_bbox,
                    confidence=conf,
                    has_helmet=(class_id == WITH_HELMET),
                    class_id=class_id,
                ))
        return detections

    def detect_riders(self, frame: np.ndarray, tracks_with_bbox: List[Tuple[int, List[float], str]]) -> dict:
        results: dict[int, List[HelmetResult]] = {}
        for track_id, bbox, category in tracks_with_bbox:
            if category in ("MOTORCYCLE", "SCOOTER", "MOPED", "CYCLE_RICKSHAW", "AUTO_RICKSHAW", "THREE_WHEELER"):
                dets = self.detect(frame, bbox)
                if dets:
                    results[track_id] = dets
        return results
