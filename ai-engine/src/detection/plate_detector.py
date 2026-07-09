import logging
import os
from typing import List, Optional

import numpy as np

from src.common.models import PlateDetection

logger = logging.getLogger(__name__)


class PlateDetector:
    def __init__(self, model_path: str, conf_threshold: float = 0.3):
        self.model_path = model_path
        self.conf_threshold = conf_threshold
        self.model: Optional = None
        self.model_version = "1.0"
        self._load_model()

    def _load_model(self):
        if not os.path.exists(self.model_path):
            logger.warning(f"Plate detection model not found at {self.model_path}")
            return
        try:
            from ultralytics import YOLO
            self.model = YOLO(self.model_path)
            # Extract version from filename
            base = os.path.basename(self.model_path)
            name = os.path.splitext(base)[0]
            self.model_version = f"yolov8-{name}"
            class_names = self.model.names if hasattr(self.model, 'names') else {}
            logger.info(f"Loaded plate detection model from {self.model_path}: "
                         f"classes={class_names}")
        except Exception as e:
            logger.error(f"Failed to load plate detection model: {e}")
            self.model = None

    def detect(self, frame: np.ndarray, roi_bbox: Optional[List[float]] = None) -> List[PlateDetection]:
        if self.model is None:
            return []

        # If ROI is given, crop frame to vehicle bbox first
        if roi_bbox and len(roi_bbox) == 4:
            h, w = frame.shape[:2]
            x1 = max(0, int(roi_bbox[0]))
            y1 = max(0, int(roi_bbox[1]))
            x2 = min(w, int(roi_bbox[2]))
            y2 = min(h, int(roi_bbox[3]))
            if x2 > x1 and y2 > y1:
                search_region = frame[y1:y2, x1:x2]
                x_offset, y_offset = x1, y1
            else:
                return []
        else:
            search_region = frame
            x_offset, y_offset = 0, 0

        if search_region.size == 0:
            return []

        try:
            results = self.model(
                search_region,
                verbose=False,
                conf=self.conf_threshold,
                iou=0.5,
                max_det=5,
            )
        except Exception as e:
            logger.warning(f"Plate detection error: {e}")
            return []

        plates = []
        for result in results:
            boxes = result.boxes
            if boxes is None:
                continue
            for i in range(len(boxes)):
                conf = float(boxes.conf[i])
                bbox = boxes.xyxy[i].tolist()
                # Offset bbox back to original frame coordinates
                abs_bbox = [
                    bbox[0] + x_offset,
                    bbox[1] + y_offset,
                    bbox[2] + x_offset,
                    bbox[3] + y_offset,
                ]
                plates.append(PlateDetection(
                    bbox=abs_bbox,
                    confidence=conf,
                ))
        return plates
