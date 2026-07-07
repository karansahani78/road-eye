import logging
from typing import List, Optional
import cv2
import numpy as np

from src.common.models import Detection, VehicleCategory

logger = logging.getLogger(__name__)

# COCO class to RoadEye category mapping for YOLOv8
COCO_TO_ROADEYE = {
    1: VehicleCategory.BICYCLE,
    2: VehicleCategory.MOTORCYCLE,
    3: VehicleCategory.CAR,
    4: VehicleCategory.BUS,
    5: VehicleCategory.TRUCK,
    6: VehicleCategory.TRUCK,  # boat -> other
    7: VehicleCategory.OTHER,  # train -> other
}

COCO_CLASS_NAMES = {
    1: "bicycle",
    2: "motorcycle",
    3: "car",
    4: "bus",
    5: "truck",
    6: "boat",
    7: "train",
}


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
            model_path = f"{self.settings.MODEL_PATH}/yolov8n.pt"
            try:
                self.model = YOLO(model_path)
                logger.info(f"Loaded YOLOv8 model from {model_path}")
            except Exception:
                logger.info("Model file not found, downloading YOLOv8n...")
                self.model = YOLO("yolov8n.pt")
                logger.info("YOLOv8n model downloaded and loaded")
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

    def detect(self, frame: np.ndarray) -> List[Detection]:
        if self.model is None:
            return self._fallback_detect(frame)
        try:
            return self._yolo_detect(frame)
        except Exception as e:
            logger.error(f"Detection error: {e}")
            return self._fallback_detect(frame)

    def _yolo_detect(self, frame: np.ndarray) -> List[Detection]:
        results = self.model(frame, verbose=False)
        detections = []
        for result in results:
            boxes = result.boxes
            if boxes is None:
                continue
            for i in range(len(boxes)):
                bbox = boxes.xyxy[i].tolist()
                confidence = float(boxes.conf[i])
                class_id = int(boxes.cls[i])
                class_name = COCO_CLASS_NAMES.get(class_id, f"class_{class_id}")
                mapped = COCO_TO_ROADEYE.get(class_id, VehicleCategory.OTHER)
                if confidence < self.settings.VEHICLE_DETECTION_CONFIDENCE:
                    continue
                detections.append(Detection(
                    bbox=bbox,
                    confidence=confidence,
                    class_id=class_id,
                    class_name=class_name,
                    mapped_category=mapped,
                ))
        return detections

    def _fallback_detect(self, frame: np.ndarray) -> List[Detection]:
        logger.debug("Using fallback detection (development mode)")
        return []
