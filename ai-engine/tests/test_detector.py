import pytest
import numpy as np
from src.detection.vehicle_detector import VehicleDetector
from src.config import Settings


class TestVehicleDetector:
    def setup_method(self):
        self.settings = Settings()
        self.detector = VehicleDetector(self.settings)

    def test_detect_empty_frame(self):
        frame = np.zeros((720, 1280, 3), dtype=np.uint8)
        detections = self.detector.detect(frame)
        assert isinstance(detections, list)
