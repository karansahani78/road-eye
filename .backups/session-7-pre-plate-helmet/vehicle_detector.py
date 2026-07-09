import logging
from datetime import datetime, timezone
from typing import List, Optional
import cv2
import numpy as np

from src.common.models import Detection, VehicleCategory

logger = logging.getLogger(__name__)

# YOLOv8n (COCO) class ID to RoadEye category mapping.
# Verified dynamically against the actual loaded model at startup.
# YOLOv8n COCO indices: 1=bicycle, 2=car, 3=motorcycle, 5=bus, 7=truck.
# Non-traffic COCO classes (person=0, airplane=4, chair=56, etc.) are silently discarded.
COCO_TO_ROADEYE = {
    1: VehicleCategory.BICYCLE,
    2: VehicleCategory.CAR,
    3: VehicleCategory.MOTORCYCLE,
    5: VehicleCategory.BUS,
    7: VehicleCategory.TRUCK,
}

COCO_CLASS_NAMES = {
    1: "bicycle",
    2: "car",
    3: "motorcycle",
    5: "bus",
    7: "truck",
}

# Set of accepted COCO class IDs — fast lookup
ACCEPTED_COCO_CLASSES = frozenset(COCO_TO_ROADEYE.keys())


class VehicleDetector:
    def __init__(self, settings):
        self.settings = settings
        self.model: Optional = None
        self.model_name = "yolov8"
        self.model_version = "1.0"
        self._load_model()

    def _load_model(self):
        try:
            from ultralytics import YOLO
            model_name = self.settings.MODEL_NAME
            model_path = f"{self.settings.MODEL_PATH}/{model_name}"
            try:
                self.model = YOLO(model_path)
                logger.info(f"Loaded YOLOv8 model from {model_path}")
            except Exception:
                logger.info(f"Model file not found at {model_path}, downloading {model_name}...")
                self.model = YOLO(model_name)
                logger.info(f"{model_name} model downloaded and loaded")
            self._verify_class_mapping()
        except ImportError:
            logger.warning("ultralytics not installed. Using DNN-based detector.")
            self._load_dnn_model()

    def _load_dnn_model(self):
        try:
            model_path = f"{self.settings.MODEL_PATH}/yolov8n.onnx"
            self.model = cv2.dnn.readNetFromONNX(model_path)
            logger.info(f"Loaded DNN model from {model_path}")
        except Exception as e:
            logger.error(f"Failed to load DNN model: {e}")
            self.model = None

    def _verify_class_mapping(self):
        if self.model is None or not hasattr(self.model, 'names'):
            return
        mismatches = []
        for cid, expected_name in COCO_CLASS_NAMES.items():
            actual_name = self.model.names.get(cid, "")
            if actual_name.lower() != expected_name.lower():
                mismatches.append(f"  class_id={cid}: code says \"{expected_name}\", model says \"{actual_name}\"")
        if mismatches:
            logger.error(f"CLASS MAPPING MISMATCH for model {self.model_name}:")
            for m in mismatches:
                logger.error(m)
            logger.error("Fix COCO_TO_ROADEYE and COCO_CLASS_NAMES in vehicle_detector.py")

    def detect(self, frame: np.ndarray) -> List[Detection]:
        if self.model is None:
            return []
        try:
            return self._yolo_detect(frame)
        except Exception as e:
            logger.error(f"Detection error: {e}")
            return []

    def _yolo_detect(self, frame: np.ndarray) -> List[Detection]:
        img_size = self.settings.DETECTION_IMAGE_SIZE
        device = self.settings.DETECTION_DEVICE or None
        results = self.model(
            frame,
            verbose=False,
            imgsz=img_size,
            conf=self.settings.DETECTION_CONFIDENCE_THRESHOLD,
            iou=self.settings.DETECTION_IOU_THRESHOLD,
            max_det=self.settings.DETECTION_MAX_DETECTIONS,
            device=device,
        )
        detections = []
        for result in results:
            boxes = result.boxes
            if boxes is None:
                continue
            for i in range(len(boxes)):
                class_id = int(boxes.cls[i])

                # REJECT non-traffic classes immediately
                if class_id not in ACCEPTED_COCO_CLASSES:
                    continue

                confidence = float(boxes.conf[i])
                bbox = boxes.xyxy[i].tolist()
                class_name = COCO_CLASS_NAMES.get(class_id, f"class_{class_id}")
                mapped = COCO_TO_ROADEYE[class_id]

                detections.append(Detection(
                    bbox=bbox,
                    confidence=confidence,
                    class_id=class_id,
                    class_name=class_name,
                    mapped_category=mapped,
                    timestamp=datetime.now(timezone.utc),
                ))
        return detections
